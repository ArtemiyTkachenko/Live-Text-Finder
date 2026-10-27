package com.artkachenko.livetextfinder.search

/** Normalized preview-local search bounds. */
internal data class NormalizedSearchArea(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite())
        require(left >= 0f && top >= 0f && right <= 1f && bottom <= 1f)
        require(right > left && bottom > top)
    }

    fun contains(points: List<PreviewPoint>, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0 || points.size != 4) return false
        val boundsLeft = left * width
        val boundsTop = top * height
        val boundsRight = right * width
        val boundsBottom = bottom * height
        return points.all { point ->
            point.x >= boundsLeft && point.x <= boundsRight &&
                point.y >= boundsTop && point.y <= boundsBottom
        }
    }

    companion object {
        val CENTERED_DEFAULT = NormalizedSearchArea(0.2f, 0.2f, 0.8f, 0.8f)

        fun fromSizeAndPosition(
            widthPercent: Int,
            heightPercent: Int,
            horizontalPercent: Int,
            verticalPercent: Int,
        ): NormalizedSearchArea {
            require(widthPercent in 20..100 && heightPercent in 20..100)
            require(horizontalPercent in 0..100 && verticalPercent in 0..100)
            val width = widthPercent / 100f
            val height = heightPercent / 100f
            val left = (1f - width) * horizontalPercent / 100f
            val top = (1f - height) * verticalPercent / 100f
            return NormalizedSearchArea(left, top, left + width, top + height)
        }
    }
}

/** Owns ephemeral area state and rejects edits created before a reset or a newer edit. */
internal class LiveSearchAreaOwner {
    var area: NormalizedSearchArea? = null
        private set
    var revision: Long = 0
        private set

    fun apply(expectedRevision: Long, newArea: NormalizedSearchArea?): Boolean {
        if (expectedRevision != revision) return false
        if (area != newArea) {
            area = newArea
            revision++
        }
        return true
    }

    fun reset() {
        area = null
        revision++
    }
}

/** Filters occurrences only when every referenced raw quad fits inside the selected area. */
internal fun filterOccurrencesToArea(
    search: TextSearchResult,
    projection: FrameProjection?,
    area: NormalizedSearchArea?,
    viewportWidth: Int,
    viewportHeight: Int,
): TextSearchResult? {
    if (area == null) return search
    if (viewportWidth <= 0 || viewportHeight <= 0 || projection == null ||
        projection.snapshot !== search.snapshot ||
        projection.target.width != viewportWidth || projection.target.height != viewportHeight
    ) {
        return null
    }
    val transform = search.snapshot?.let { snapshot ->
        uprightToPreview(snapshot.frame, projection.target)
    } ?: return null
    val eligible = search.occurrences.filter { occurrence ->
        if (occurrence.elementIndices.isEmpty()) return@filter false
        val line = search.snapshot?.lines?.getOrNull(occurrence.lineIndex) ?: return@filter false
        occurrence.elementIndices.all { elementIndex ->
            val element = line.elements.getOrNull(elementIndex) ?: return@all false
            val points = elementPolygon(element)?.map { point ->
                transform.map(point) ?: return@all false
            } ?: return@all false
            validPolygon(points) && area.contains(points, viewportWidth, viewportHeight)
        }
    }
    return search.copy(occurrences = eligible)
}
