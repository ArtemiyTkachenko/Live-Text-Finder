package com.artkachenko.livetextfinder.ocr

import android.graphics.Rect
import android.graphics.Point
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

/** All entry points and callbacks run on the main executor; recognition itself is asynchronous. */
internal class MlKitTextAnalyzer(
    executor: Executor,
    private val captureIdentity: () -> FrameIdentity,
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
    private val onState: (OcrUiState, FrameIdentity) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val lifetime = AnalysisLifetime { recognizer.close() }
    private var disposed = false
    private var pendingFrame: Pair<FrameInfo, FrameIdentity>? = null
    private val delegate = MlKitAnalyzer(
        listOf(recognizer), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, executor,
    ) { result ->
        val frame = pendingFrame
        pendingFrame = null
        try {
            if (!disposed && frame != null) {
                val error = result.getThrowable(recognizer)
                val text = result.getValue(recognizer)
                when {
                    error != null -> onState(OcrUiState(OcrStatus.Error, "Text recognition failed. Retry the camera."), frame.second)
                    text == null -> onState(OcrUiState(OcrStatus.Error, "No recognition result was returned. Retry the camera."), frame.second)
                    else -> {
                        val snapshot = text.toSnapshot(frame.first)
                        onState(OcrUiState(
                            if (snapshot.lines.isEmpty()) OcrStatus.Scanning else OcrStatus.TextRecognized,
                            if (snapshot.lines.isEmpty()) "Scanning for readable printed text…" else "Receiving live text results.",
                            snapshot,
                        ), frame.second)
                    }
                }
            }
        } finally {
            lifetime.finish()
        }
    }

    override fun getDefaultTargetResolution(): Size = delegate.defaultTargetResolution

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        // CameraX can deliver another frame after MlKitAnalyzer closes the image but before
        // its result callback executes. Keep metadata and recognizer ownership bounded too.
        if (!lifetime.tryBegin()) {
            image.close()
            return
        }
        val identity = captureIdentity()
        try {
            if (image.image == null) {
                image.close()
                lifetime.finish()
                if (!disposed) onState(OcrUiState(OcrStatus.Error, "Camera supplied an unreadable frame."), identity)
                return
            }
            val matrix = FloatArray(9)
            image.imageInfo.sensorToBufferTransformMatrix.getValues(matrix)
            pendingFrame = FrameInfo(
                image.imageInfo.timestamp, image.width, image.height,
                image.imageInfo.rotationDegrees, image.cropRect.toBounds(), matrix.toList(),
            ) to identity
            // Ownership transfers here: MlKitAnalyzer closes the proxy on completion/errors.
            delegate.analyze(image)
        } catch (error: Exception) {
            image.close()
            pendingFrame = null
            lifetime.finish()
            if (!disposed) onState(OcrUiState(OcrStatus.Error, "Unable to analyze the camera frame. Retry the camera."), identity)
        }
    }

    override fun close() {
        disposed = true
        lifetime.close()
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
