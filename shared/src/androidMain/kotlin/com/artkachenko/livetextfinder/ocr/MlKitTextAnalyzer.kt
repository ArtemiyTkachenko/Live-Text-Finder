package com.artkachenko.livetextfinder.ocr

import android.graphics.Rect
import android.util.Size
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor

/** All entry points and callbacks run on the main executor; recognition itself is asynchronous. */
internal class MlKitTextAnalyzer(
    executor: Executor,
    private val onState: (OcrUiState) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val lifetime = AnalysisLifetime { recognizer.close() }
    private var disposed = false
    private var pendingFrame: FrameInfo? = null
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
                    error != null -> onState(OcrUiState(OcrStatus.Error, "Text recognition failed. Retry the camera."))
                    text == null -> onState(OcrUiState(OcrStatus.Error, "No recognition result was returned. Retry the camera."))
                    else -> {
                        val snapshot = text.toSnapshot(frame)
                        onState(OcrUiState(
                            if (snapshot.lines.isEmpty()) OcrStatus.Scanning else OcrStatus.TextRecognized,
                            if (snapshot.lines.isEmpty()) "Scanning for readable printed text…" else "Receiving live text results.",
                            snapshot,
                        ))
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
        try {
            if (image.image == null) {
                image.close()
                lifetime.finish()
                if (!disposed) onState(OcrUiState(OcrStatus.Error, "Camera supplied an unreadable frame."))
                return
            }
            val matrix = FloatArray(9)
            image.imageInfo.sensorToBufferTransformMatrix.getValues(matrix)
            pendingFrame = FrameInfo(
                image.imageInfo.timestamp, image.width, image.height,
                image.imageInfo.rotationDegrees, image.cropRect.toBounds(), matrix.toList(),
            )
            // Ownership transfers here: MlKitAnalyzer closes the proxy on completion/errors.
            delegate.analyze(image)
        } catch (error: Exception) {
            image.close()
            pendingFrame = null
            lifetime.finish()
            if (!disposed) onState(OcrUiState(OcrStatus.Error, "Unable to analyze the camera frame. Retry the camera."))
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
                line.cornerPoints?.map { TextPoint(it.x, it.y) }.orEmpty(),
                line.elements.map { element ->
                    TextElement(
                        element.text, element.boundingBox?.toBounds(),
                        element.cornerPoints?.map { TextPoint(it.x, it.y) }.orEmpty(),
                    )
                },
            )
        }
    },
    frame = frame,
)
