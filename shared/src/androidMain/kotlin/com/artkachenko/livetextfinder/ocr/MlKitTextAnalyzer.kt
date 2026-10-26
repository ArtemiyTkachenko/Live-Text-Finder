package com.artkachenko.livetextfinder.ocr

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import com.artkachenko.livetextfinder.search.FrameIdentity
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** A copied proxy image paired with OCR produced from that same proxy. */
internal data class CapturedCameraFrame(
    val requestId: Long,
    val bitmap: Bitmap,
    val snapshot: OcrSnapshot,
    val identity: FrameIdentity,
)

private data class PendingFrame(
    val frameInfo: FrameInfo,
    val identity: FrameIdentity,
    val capture: CaptureWork?,
)

private class CaptureWork(
    val requestId: Long,
    val image: ImageProxy,
    val frameInfo: FrameInfo,
    val identity: FrameIdentity,
) {
    val cancelled = AtomicBoolean(false)
    var bitmap: Bitmap? = null
}

/** All entry points and callbacks run on the main executor; recognition itself is asynchronous. */
internal class MlKitTextAnalyzer(
    private val executor: Executor,
    private val captureIdentity: () -> FrameIdentity,
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
    private val captureExecutor: Executor = executor,
    private val copyImage: (ImageProxy) -> Bitmap = ImageProxy::toBitmap,
    private val onCaptured: (CapturedCameraFrame) -> Boolean = { false },
    private val onCaptureFailure: (Long, String) -> Unit = { _, _ -> },
    private val onState: (OcrUiState, FrameIdentity) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val lifetime = AnalysisLifetime { recognizer.close() }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var disposed = false
    private var queuedCaptureId: Long? = null
    private var activeCapture: CaptureWork? = null
    private var pendingFrame: PendingFrame? = null
    private val delegate = MlKitAnalyzer(
        listOf(recognizer), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, executor,
    ) { result ->
        val pending = pendingFrame
        pendingFrame = null
        try {
            if (!disposed && pending != null) {
                deliverResult(result, pending)
            } else {
                discardCapture(pending?.capture)
            }
        } finally {
            activeCapture = null
            lifetime.finish()
        }
    }

    override fun getDefaultTargetResolution(): Size = delegate.defaultTargetResolution

    /** Requests capture of the next proxy that the analyzer accepts. */
    fun requestCapture(requestId: Long): Boolean {
        if (disposed || queuedCaptureId != null || activeCapture != null) {
            return false
        }
        queuedCaptureId = requestId
        return true
    }

    /** Invalidates queued or in-flight capture without taking ownership from the recognizer. */
    fun cancelCapture(requestId: Long) {
        if (queuedCaptureId == requestId) {
            queuedCaptureId = null
        }
        activeCapture?.takeIf { it.requestId == requestId }?.let { capture ->
            capture.cancelled.set(true)
            if (pendingFrame?.capture === capture) {
                discardCapture(capture)
            }
        }
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        if (!lifetime.tryBegin()) {
            image.close()
            return
        }
        val identity = captureIdentity()
        val frame = try {
            image.frameInfo()
        } catch (error: Exception) {
            image.close()
            lifetime.finish()
            failQueuedCapture("Camera supplied an unreadable frame.")
            if (!disposed) {
                onState(
                    OcrUiState(OcrStatus.Error, "Camera supplied an unreadable frame."),
                    identity,
                )
            }
            return
        }
        val requestId = queuedCaptureId
        queuedCaptureId = null
        if (requestId == null) {
            analyzeNormally(image, PendingFrame(frame, identity, null))
            return
        }
        if (!image.hasReadableImage()) {
            image.close()
            lifetime.finish()
            onCaptureFailure(requestId, "Camera supplied an unreadable frame.")
            if (!disposed) {
                onState(
                    OcrUiState(OcrStatus.Error, "Camera supplied an unreadable frame."),
                    identity,
                )
            }
            return
        }
        val pixelCount = frame.bufferWidth.toLong() * frame.bufferHeight.toLong()
        if (frame.bufferWidth <= 0 || frame.bufferHeight <= 0 ||
            frame.rotationDegrees !in VALID_ROTATIONS ||
            pixelCount !in 1..MAX_CAPTURE_PIXELS
        ) {
            image.close()
            lifetime.finish()
            onCaptureFailure(requestId, "Camera frame dimensions are invalid for a safe freeze.")
            return
        }

        val work = CaptureWork(requestId, image, frame, identity)
        activeCapture = work
        try {
            captureExecutor.execute {
                val bitmap = runCatching { copyImage(image) }.getOrNull()
                try {
                    executor.execute { finishCopy(work, bitmap) }
                } catch (error: RuntimeException) {
                    val posted = mainHandler.post { failCopyHandoff(work, bitmap) }
                    if (!posted) {
                        bitmap?.recycle()
                        image.close()
                        lifetime.finish()
                    }
                }
            }
        } catch (error: RuntimeException) {
            activeCapture = null
            image.close()
            lifetime.finish()
            onCaptureFailure(requestId, "Could not start the frame copy. Retry freeze.")
        }
    }

    private fun failCopyHandoff(work: CaptureWork, bitmap: Bitmap?) {
        bitmap?.recycle()
        work.image.close()
        if (activeCapture === work) {
            activeCapture = null
            lifetime.finish()
            onCaptureFailure(work.requestId, "Could not finish the frame copy. Retry freeze.")
        }
    }

    private fun finishCopy(work: CaptureWork, bitmap: Bitmap?) {
        if (activeCapture !== work || disposed || work.cancelled.get()) {
            bitmap?.recycle()
            work.image.close()
            activeCapture = null
            lifetime.finish()
            return
        }
        if (bitmap == null) {
            work.image.close()
            activeCapture = null
            lifetime.finish()
            onCaptureFailure(work.requestId, "Could not copy the camera frame. Retry freeze.")
            return
        }
        work.bitmap = bitmap
        pendingFrame = PendingFrame(work.frameInfo, work.identity, work)
        try {
            // The recognizer receives the same proxy only after its pixels have been copied.
            delegate.analyze(work.image)
        } catch (error: RuntimeException) {
            work.image.close()
            pendingFrame = null
            activeCapture = null
            discardCapture(work)
            lifetime.finish()
            onCaptureFailure(work.requestId, "Unable to analyze the captured frame. Retry freeze.")
        }
    }

    private fun analyzeNormally(image: ImageProxy, pending: PendingFrame) {
        if (!image.hasReadableImage()) {
            image.close()
            lifetime.finish()
            if (!disposed) {
                onState(
                    OcrUiState(OcrStatus.Error, "Camera supplied an unreadable frame."),
                    pending.identity,
                )
            }
            return
        }
        pendingFrame = pending
        try {
            delegate.analyze(image)
        } catch (error: RuntimeException) {
            image.close()
            pendingFrame = null
            lifetime.finish()
            if (!disposed) {
                onState(
                    OcrUiState(
                        OcrStatus.Error,
                        "Unable to analyze the camera frame. Retry the camera.",
                    ),
                    pending.identity,
                )
            }
        }
    }

    private fun deliverResult(
        result: MlKitAnalyzer.Result,
        pending: PendingFrame,
    ) {
        val capture = pending.capture
        val error = runCatching { result.getThrowable(recognizer) }.getOrNull()
        val text = if (error == null) {
            runCatching { result.getValue(recognizer) }.getOrNull()
        } else {
            null
        }
        if (error != null || text == null) {
            val message = if (error != null) {
                "Text recognition failed. Retry the camera."
            } else {
                "No recognition result was returned. Retry the camera."
            }
            if (capture != null) {
                onCaptureFailure(capture.requestId, message)
                discardCapture(capture)
            }
            onState(OcrUiState(OcrStatus.Error, message), pending.identity)
            return
        }
        val snapshot = text.toSnapshot(pending.frameInfo)
        val resultState = OcrUiState(
            if (snapshot.lines.isEmpty()) OcrStatus.Scanning else OcrStatus.TextRecognized,
            if (snapshot.lines.isEmpty()) {
                "Scanning for readable printed text…"
            } else {
                "Receiving live text results."
            },
            snapshot,
        )
        onState(resultState, pending.identity)
        if (capture != null) {
            val bitmap = capture.bitmap
            if (bitmap != null && !capture.cancelled.get() && !disposed) {
                val accepted = runCatching {
                    onCaptured(
                        CapturedCameraFrame(
                            capture.requestId,
                            bitmap,
                            snapshot,
                            pending.identity,
                        ),
                    )
                }.getOrDefault(false)
                if (accepted) {
                    capture.bitmap = null
                } else {
                    onCaptureFailure(capture.requestId, "The frozen frame was no longer current.")
                }
            } else if (!capture.cancelled.get()) {
                onCaptureFailure(capture.requestId, "The freeze request is no longer active.")
            }
            discardCapture(capture)
        }
    }

    private fun failQueuedCapture(message: String) {
        val requestId = queuedCaptureId ?: return
        queuedCaptureId = null
        onCaptureFailure(requestId, message)
    }

    private fun discardCapture(work: CaptureWork?) {
        work?.bitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            work.bitmap = null
        }
    }

    private fun ImageProxy.hasReadableImage(): Boolean =
        runCatching { image != null }.getOrDefault(false)

    private fun ImageProxy.frameInfo(): FrameInfo {
        val image = image ?: error("Camera image unavailable")
        val matrix = FloatArray(9)
        imageInfo.sensorToBufferTransformMatrix.getValues(matrix)
        return FrameInfo(
            imageInfo.timestamp,
            width,
            height,
            imageInfo.rotationDegrees,
            cropRect.toBounds(),
            matrix.toList(),
        )
    }

    override fun close() {
        disposed = true
        queuedCaptureId = null
        activeCapture?.let { capture ->
            capture.cancelled.set(true)
            if (pendingFrame?.capture === capture) {
                discardCapture(capture)
            }
        }
        lifetime.close()
    }

    private companion object {
        const val MAX_CAPTURE_PIXELS = 4_000_000L
        val VALID_ROTATIONS = setOf(0, 90, 180, 270)
    }
}

private fun Rect.toBounds() = TextBounds(left, top, right, bottom)

internal fun Text.toSnapshot(frame: FrameInfo) = OcrSnapshot(
    text = text,
    lines = textBlocks.flatMap { block ->
        block.lines.map { line ->
            TextLine(
                line.text, line.boundingBox?.toBounds(),
                frame.bundledCornersToUpright(line.cornerPoints),
                line.elements.map { element ->
                    TextElement(
                        element.text, element.boundingBox?.toBounds(),
                        frame.bundledCornersToUpright(element.cornerPoints),
                    )
                },
            )
        }
    },
    frame = frame,
)

/**
 * Pinned bundled Latin bridge rotates boundingBox, but leaves cornerPoints in raw-buffer space.
 * Normalize only corners here so both representations satisfy the common upright contract.
 * See CS-003 SDK-adapter regression; do not apply this conversion again in preview projection.
 */
private fun FrameInfo.bundledCornersToUpright(points: Array<Point>?): List<TextPoint> =
    points.orEmpty().mapNotNull { point ->
        when (rotationDegrees) {
            0 -> TextPoint(point.x, point.y)
            90 -> TextPoint(bufferHeight - point.y, point.x)
            180 -> TextPoint(bufferWidth - point.x, bufferHeight - point.y)
            270 -> TextPoint(point.y, bufferWidth - point.x)
            else -> null
        }
    }
