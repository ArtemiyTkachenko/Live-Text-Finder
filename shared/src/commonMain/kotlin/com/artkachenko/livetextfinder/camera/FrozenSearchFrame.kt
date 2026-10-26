package com.artkachenko.livetextfinder.camera

import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.search.FrameIdentity
import com.artkachenko.livetextfinder.search.PreviewPoint

/** Identifies the source OCR result without retaining Android image-buffer types. */
internal data class FrozenSearchFrame(
    val requestId: Long,
    val identity: FrameIdentity,
    val snapshot: OcrSnapshot,
)

/** Presentation state for one freeze request; bitmap ownership stays on Android. */
internal data class FreezePresentationState(
    val mode: FreezeMode = FreezeMode.Live,
    val requestId: Long? = null,
    val frame: FrozenSearchFrame? = null,
    val error: String? = null,
)

internal enum class FreezeMode {
    Live,
    Freezing,
    Frozen,
}

/** Maps a raw camera buffer into a centered, upright, aspect-preserving viewport. */
internal data class FrozenFrameFit(
    val rawWidth: Int,
    val rawHeight: Int,
    val rotationDegrees: Int,
    val viewportWidth: Float,
    val viewportHeight: Float,
) {
    val uprightWidth: Int = if (rotationDegrees % 180 == 0) rawWidth else rawHeight
    val uprightHeight: Int = if (rotationDegrees % 180 == 0) rawHeight else rawWidth
    val scale: Float
    val left: Float
    val top: Float
    val isValid: Boolean

    init {
        isValid = rawWidth > 0 && rawHeight > 0 &&
            rotationDegrees in listOf(0, 90, 180, 270) &&
            viewportWidth.isFinite() && viewportHeight.isFinite() &&
            viewportWidth > 0f && viewportHeight > 0f
        scale = if (isValid) {
            minOf(viewportWidth / uprightWidth, viewportHeight / uprightHeight)
        } else {
            0f
        }
        left = (viewportWidth - uprightWidth * scale) / 2f
        top = (viewportHeight - uprightHeight * scale) / 2f
    }

    fun mapUprightPoint(point: PreviewPoint): PreviewPoint? {
        if (!isValid || !point.x.isFinite() || !point.y.isFinite()) {
            return null
        }
        return PreviewPoint(left + point.x * scale, top + point.y * scale)
    }

    fun mapRawPoint(point: PreviewPoint): PreviewPoint? {
        if (!isValid) {
            return null
        }
        val upright = when (rotationDegrees) {
            90 -> PreviewPoint(rawHeight - point.y, point.x)
            180 -> PreviewPoint(rawWidth - point.x, rawHeight - point.y)
            270 -> PreviewPoint(point.y, rawWidth - point.x)
            else -> point
        }
        return mapUprightPoint(upright)
    }
}
