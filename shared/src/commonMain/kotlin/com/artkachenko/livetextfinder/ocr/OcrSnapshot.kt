package com.artkachenko.livetextfinder.ocr

/** Coordinates are pixels in the input buffer after applying [FrameInfo.rotationDegrees]. */
internal data class TextPoint(val x: Int, val y: Int)
internal data class TextBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
internal data class TextElement(
    val text: String,
    val bounds: TextBounds?,
    val corners: List<TextPoint>,
)
internal data class TextLine(
    val text: String,
    val bounds: TextBounds?,
    val corners: List<TextPoint>,
    val elements: List<TextElement>,
)

/** Crop and sensor matrix refer to the unrotated buffer; timestamps use CameraX's timebase. */
internal data class FrameInfo(
    val timestampNanos: Long,
    val bufferWidth: Int,
    val bufferHeight: Int,
    val rotationDegrees: Int,
    val crop: TextBounds,
    val sensorToBufferMatrix: List<Float>,
) {
    val uprightWidth: Int get() = if (rotationDegrees % 180 == 0) bufferWidth else bufferHeight
    val uprightHeight: Int get() = if (rotationDegrees % 180 == 0) bufferHeight else bufferWidth
}

internal data class OcrSnapshot(val text: String, val lines: List<TextLine>, val frame: FrameInfo)

internal enum class OcrStatus {
    PermissionRequired, Starting, Scanning, TextRecognized, Paused, Unavailable, Error,
}

internal data class OcrUiState(
    val status: OcrStatus = OcrStatus.PermissionRequired,
    val message: String = "Allow camera access to scan printed Latin text.",
    val snapshot: OcrSnapshot? = null,
)
