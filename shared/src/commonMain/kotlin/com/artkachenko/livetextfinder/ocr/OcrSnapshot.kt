package com.artkachenko.livetextfinder.ocr

import com.artkachenko.livetextfinder.search.FrameProjection

/** Adapter-normalized upright pixel geometry from the SDK. */
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

internal interface RecognizedTextContent {
    val text: String
    val lines: List<TextLine>
}

/** Camera recognition retains the real frame metadata used by live projection. */
internal data class OcrSnapshot(
    override val text: String,
    override val lines: List<TextLine>,
    val frame: FrameInfo,
) : RecognizedTextContent

/** Imported recognition geometry belongs to the normalized still image. */
internal data class ImportedTextSnapshot(
    override val text: String,
    override val lines: List<TextLine>,
    val width: Int,
    val height: Int,
    val sourceIdentity: Long,
) : RecognizedTextContent

internal enum class OcrStatus {
    PermissionRequired, Starting, Scanning, TextRecognized, Paused, Unavailable, Error,
}

internal data class OcrUiState(
    val status: OcrStatus = OcrStatus.PermissionRequired,
    val message: String = "Allow camera access to scan printed Latin text.",
    val snapshot: OcrSnapshot? = null,
    val projection: FrameProjection? = null,
    /** Monotonic receipt time supplied by the live frame owner. */
    val receivedAtMillis: Long? = null,
)
