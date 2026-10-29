package com.artkachenko.livetextfinder.camera

import com.artkachenko.livetextfinder.search.PreviewPoint

/** A bounded presentation transform for a retained still image. */
internal data class StillViewTransform(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    val isRenderable: Boolean
        get() = zoom.isFinite() && zoom in MIN_ZOOM..MAX_ZOOM && panX.isFinite() && panY.isFinite()

    fun reset(): StillViewTransform = StillViewTransform()

    fun zoomBy(
        factor: Float,
        centroid: PreviewPoint,
        viewportWidth: Float,
        viewportHeight: Float,
        fit: FrozenFrameFit,
        panX: Float = 0f,
        panY: Float = 0f,
    ): StillViewTransform {
        if (!isRenderable) return StillViewTransform()
        if (!factor.isFinite() || factor <= 0f || !validViewport(viewportWidth, viewportHeight) ||
            !fit.isValid || !centroid.x.isFinite() || !centroid.y.isFinite() ||
            !panX.isFinite() || !panY.isFinite()
        ) {
            return this
        }
        val nextZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val centerX = viewportWidth / 2f
        val centerY = viewportHeight / 2f
        val ratio = nextZoom / zoom
        val nextX = centroid.x - centerX - ratio * (centroid.x - centerX - this.panX) + panX
        val nextY = centroid.y - centerY - ratio * (centroid.y - centerY - this.panY) + panY
        return bounded(nextZoom, nextX, nextY, viewportWidth, viewportHeight, fit)
    }

    fun panBy(
        deltaX: Float,
        deltaY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        fit: FrozenFrameFit,
    ): StillViewTransform {
        if (!isRenderable) return StillViewTransform()
        if (!deltaX.isFinite() || !deltaY.isFinite() ||
            !validViewport(viewportWidth, viewportHeight) ||
            !fit.isValid
        ) {
            return this
        }
        return bounded(zoom, panX + deltaX, panY + deltaY, viewportWidth, viewportHeight, fit)
    }

    fun map(point: PreviewPoint, viewportWidth: Float, viewportHeight: Float): PreviewPoint? {
        if (!point.x.isFinite() || !point.y.isFinite() ||
            !validViewport(viewportWidth, viewportHeight) || !isRenderable
        ) {
            return null
        }
        val centerX = viewportWidth / 2f
        val centerY = viewportHeight / 2f
        return PreviewPoint(
            centerX + zoom * (point.x - centerX) + panX,
            centerY + zoom * (point.y - centerY) + panY,
        )
    }

    private fun bounded(
        zoom: Float,
        panX: Float,
        panY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        fit: FrozenFrameFit,
    ): StillViewTransform {
        if (zoom == MIN_ZOOM) return StillViewTransform()
        val maxX = ((fit.uprightWidth * fit.scale * zoom - viewportWidth) / 2f).coerceAtLeast(0f)
        val maxY = ((fit.uprightHeight * fit.scale * zoom - viewportHeight) / 2f).coerceAtLeast(0f)
        return StillViewTransform(zoom, panX.coerceIn(-maxX, maxX), panY.coerceIn(-maxY, maxY))
    }

    private fun validViewport(width: Float, height: Float) =
        width.isFinite() && height.isFinite() && width > 0f && height > 0f

    private companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 4f
    }
}
