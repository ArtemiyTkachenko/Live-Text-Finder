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

internal enum class SearchAreaCorner { TopLeft, TopRight, BottomLeft, BottomRight }

/** Keeps an explicit Full frame precision result as the owner's null-area representation. */
internal fun normalizeEditorDraftArea(area: NormalizedSearchArea?): NormalizedSearchArea? =
    area?.takeUnless {
        it.left == 0f && it.top == 0f && it.right == 1f && it.bottom == 1f
    }

/** Returns the closest corner whose clamped 48dp-style target contains the down point. */
internal fun searchAreaCornerAt(
    area: NormalizedSearchArea,
    x: Float,
    y: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    targetSize: Float,
): SearchAreaCorner? {
    if (!x.isFinite() || !y.isFinite() || !viewportWidth.isFinite() ||
        !viewportHeight.isFinite() || !targetSize.isFinite() || viewportWidth <= 0f ||
        viewportHeight <= 0f || targetSize <= 0f
    ) return null
    val width = targetSize.coerceAtMost(viewportWidth)
    val height = targetSize.coerceAtMost(viewportHeight)
    val candidates = SearchAreaCorner.values().mapNotNull { corner ->
        val cornerX = when (corner) {
            SearchAreaCorner.TopLeft, SearchAreaCorner.BottomLeft -> area.left * viewportWidth
            SearchAreaCorner.TopRight, SearchAreaCorner.BottomRight -> area.right * viewportWidth
        }
        val cornerY = when (corner) {
            SearchAreaCorner.TopLeft, SearchAreaCorner.TopRight -> area.top * viewportHeight
            SearchAreaCorner.BottomLeft, SearchAreaCorner.BottomRight -> {
                area.bottom * viewportHeight
            }
        }
        val left = (cornerX - width / 2f).coerceIn(0f, viewportWidth - width)
        val top = (cornerY - height / 2f).coerceIn(0f, viewportHeight - height)
        if (x !in left..(left + width) || y !in top..(top + height)) return@mapNotNull null
        val centerX = left + width / 2f
        val centerY = top + height / 2f
        val dx = x - centerX
        val dy = y - centerY
        Triple(corner, dx * dx + dy * dy, corner.ordinal)
    }
    return candidates.minWithOrNull(compareBy<Triple<SearchAreaCorner, Float, Int>> { it.second }
        .thenBy { it.third })?.first
}

/** Moves normalized bounds by preview-local pixels while preserving their size. */
internal fun moveSearchArea(
    area: NormalizedSearchArea,
    deltaX: Float,
    deltaY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): NormalizedSearchArea? {
    if (!deltaX.isFinite() || !deltaY.isFinite() || !viewportWidth.isFinite() ||
        !viewportHeight.isFinite() || viewportWidth <= 0f || viewportHeight <= 0f
    ) return null
    val width = area.right - area.left
    val height = area.bottom - area.top
    val left = (area.left + deltaX / viewportWidth).coerceIn(0f, 1f - width)
    val top = (area.top + deltaY / viewportHeight).coerceIn(0f, 1f - height)
    return NormalizedSearchArea(left, top, left + width, top + height)
}

/** Resizes from one corner, anchoring the opposite corner and enforcing a 20% minimum. */
internal fun resizeSearchArea(
    area: NormalizedSearchArea,
    corner: SearchAreaCorner,
    deltaX: Float,
    deltaY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): NormalizedSearchArea? {
    if (!deltaX.isFinite() || !deltaY.isFinite() || !viewportWidth.isFinite() ||
        !viewportHeight.isFinite() || viewportWidth <= 0f || viewportHeight <= 0f
    ) return null
    val dx = deltaX / viewportWidth
    val dy = deltaY / viewportHeight
    val minSize = 0.2f
    return when (corner) {
        SearchAreaCorner.TopLeft -> {
            val left = (area.left + dx).coerceIn(0f, area.right - minSize)
            val top = (area.top + dy).coerceIn(0f, area.bottom - minSize)
            NormalizedSearchArea(left, top, area.right, area.bottom)
        }
        SearchAreaCorner.TopRight -> {
            val right = (area.right + dx).coerceIn(area.left + minSize, 1f)
            val top = (area.top + dy).coerceIn(0f, area.bottom - minSize)
            NormalizedSearchArea(area.left, top, right, area.bottom)
        }
        SearchAreaCorner.BottomLeft -> {
            val left = (area.left + dx).coerceIn(0f, area.right - minSize)
            val bottom = (area.bottom + dy).coerceIn(area.top + minSize, 1f)
            NormalizedSearchArea(left, area.top, area.right, bottom)
        }
        SearchAreaCorner.BottomRight -> {
            val right = (area.right + dx).coerceIn(area.left + minSize, 1f)
            val bottom = (area.bottom + dy).coerceIn(area.top + minSize, 1f)
            NormalizedSearchArea(area.left, area.top, right, bottom)
        }
    }
}

/** Accessibility corner step; dx/dy are signed 5% travel directions. */
internal fun resizeSearchAreaByStep(
    area: NormalizedSearchArea,
    corner: SearchAreaCorner,
    horizontalStep: Int,
    verticalStep: Int,
    viewportWidth: Float,
    viewportHeight: Float,
): NormalizedSearchArea? = resizeSearchArea(
    area,
    corner,
    horizontalStep * viewportWidth * 0.05f,
    verticalStep * viewportHeight * 0.05f,
    viewportWidth,
    viewportHeight,
)

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

/** Owns one temporary UI draft and its callback token; committed area remains in the owner. */
internal class LiveSearchAreaEditorSession(private val owner: LiveSearchAreaOwner) {
    internal data class Draft(
        val token: Long,
        val expectedRevision: Long,
        val area: NormalizedSearchArea?,
    )

    var draft: Draft? = null
        private set
    private var nextToken = 0L

    fun begin(area: NormalizedSearchArea): Draft {
        nextToken++
        return Draft(nextToken, owner.revision, area).also { draft = it }
    }

    fun update(token: Long, area: NormalizedSearchArea?): Boolean {
        val current = draft?.takeIf { it.token == token } ?: return false
        draft = current.copy(area = area)
        return true
    }

    fun cancel(token: Long): Boolean {
        if (draft?.token != token) return false
        draft = null
        return true
    }

    fun cancelCurrent() {
        draft = null
        nextToken++
    }

    fun precisionAction(
        token: Long,
        isCurrent: () -> Boolean,
        onOpen: (Long) -> Unit,
    ): () -> Unit = {
        val current = draft
        if (current?.token == token && current.expectedRevision == owner.revision && isCurrent()) {
            onOpen(token)
        }
    }

    fun apply(token: Long, isCurrent: () -> Boolean): Boolean {
        val current = draft?.takeIf { it.token == token } ?: return false
        if (!isCurrent() || !owner.apply(current.expectedRevision, current.area)) return false
        draft = null
        return true
    }
}

/** Guards one precision dialog instance so stale Apply/Cancel callbacks cannot affect a reopen. */
internal class LiveSearchAreaDialogSession {
    private var nextToken = 0L
    var currentToken: Long? = null
        private set

    fun open(): Long {
        nextToken++
        currentToken = nextToken
        return nextToken
    }

    fun dispatch(token: Long, action: () -> Unit): Boolean {
        if (currentToken != token) return false
        action()
        return true
    }

    fun dismiss(token: Long, action: () -> Unit): Boolean {
        if (currentToken != token) return false
        currentToken = null
        action()
        return true
    }

    fun invalidate() {
        currentToken = null
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
