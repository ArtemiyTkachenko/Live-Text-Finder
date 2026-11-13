package com.artkachenko.livetextfinder.search

import com.artkachenko.livetextfinder.camera.FrozenFrameFit
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.ImportedImageState
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import kotlin.math.abs

/** Projects frozen matches through the same centered Fit transform used by the Android renderer. */
internal fun projectFrozenHighlights(
    search: TextSearchResult,
    viewportWidth: Float,
    viewportHeight: Float,
): Highlights {
    val snapshot = search.snapshot ?: return Highlights(false, emptyList())
    val frame = snapshot.frame
    val fit = FrozenFrameFit(
        frame.bufferWidth,
        frame.bufferHeight,
        frame.rotationDegrees,
        viewportWidth,
        viewportHeight,
    )
    if (!fit.isValid) return Highlights(false, emptyList())
    val elements = search.occurrences.flatMap { occurrence ->
        occurrence.elementIndices.map { occurrence.lineIndex to it }
    }.distinct()
    val outlines = elements.mapNotNull { (lineIndex, elementIndex) ->
        val element = snapshot.lines.getOrNull(lineIndex)?.elements?.getOrNull(elementIndex)
            ?: return@mapNotNull null
        val points = elementPolygon(element)?.map { point ->
            fit.mapUprightPoint(point) ?: return@mapNotNull null
        } ?: return@mapNotNull null
        if (!validPolygon(points) ||
            !intersectsPreview(points, viewportWidth.toInt(), viewportHeight.toInt())
        ) {
            null
        } else {
            ElementOutline(
                lineIndex,
                elementIndex,
                points,
                ownerIds(search, lineIndex, elementIndex),
            )
        }
    }
    return Highlights(true, outlines)
}

/** Fits imported OCR geometry over its normalized still bitmap using the same Fit transform. */
internal fun projectImportedHighlights(
    search: TextSearchResult,
    snapshot: ImportedTextSnapshot,
    viewportWidth: Float,
    viewportHeight: Float,
): Highlights {
    if (search.content !== snapshot || snapshot.width <= 0 || snapshot.height <= 0) {
        return Highlights(false, emptyList())
    }
    val fit = FrozenFrameFit(
        snapshot.width,
        snapshot.height,
        0,
        viewportWidth,
        viewportHeight,
    )
    if (!fit.isValid) {
        return Highlights(false, emptyList())
    }
    val elements = search.occurrences.flatMap { occurrence ->
        occurrence.elementIndices.map { occurrence.lineIndex to it }
    }.distinct()
    val outlines = elements.mapNotNull { (lineIndex, elementIndex) ->
        val element = snapshot.lines.getOrNull(lineIndex)?.elements?.getOrNull(elementIndex)
            ?: return@mapNotNull null
        val points = elementPolygon(element)?.map { point ->
            fit.mapUprightPoint(point) ?: return@mapNotNull null
        } ?: return@mapNotNull null
        if (!validPolygon(points) ||
            !intersectsPreview(points, viewportWidth.toInt(), viewportHeight.toInt())
        ) {
            null
        } else {
            ElementOutline(
                lineIndex,
                elementIndex,
                points,
                ownerIds(search, lineIndex, elementIndex),
            )
        }
    }
    return Highlights(true, outlines)
}

/** Rechecks import and search identities before exposing outlines at draw time. */
internal fun importedHighlightsForDraw(
    search: TextSearchResult,
    displayedSnapshot: ImportedTextSnapshot?,
    currentImportState: ImportedImageState,
    displayedSearchIdentity: String,
    currentSearchIdentity: String,
    viewportWidth: Float,
    viewportHeight: Float,
    contentVisible: Boolean,
): Highlights {
    if (!contentVisible || displayedSnapshot == null ||
        currentImportState.phase != ImportedImagePhase.Imported ||
        currentImportState.snapshot !== displayedSnapshot ||
        displayedSearchIdentity != currentSearchIdentity ||
        search.spec.identity != displayedSearchIdentity || search.content !== displayedSnapshot
    ) {
        return Highlights(false, emptyList())
    }
    return projectImportedHighlights(search, displayedSnapshot, viewportWidth, viewportHeight)
}

/** Reuses the exact frame only; overlay and native preview share origin and physical pixel size. */
internal fun projectHighlights(
    search: TextSearchResult,
    projection: FrameProjection?,
    overlayWidth: Int,
    overlayHeight: Int,
): Highlights {
    if (projection == null || projection.snapshot !== search.snapshot ||
        projection.target.width != overlayWidth || projection.target.height != overlayHeight
    ) return Highlights(false, emptyList())
    val matrix = uprightToPreview(projection.snapshot.frame, projection.target)
        ?: return Highlights(false, emptyList())
    val elements = search.occurrences.flatMap { occurrence ->
        occurrence.elementIndices.map { occurrence.lineIndex to it }
    }.distinct()
    val outlines = elements.mapNotNull { (line, index) ->
        val element = projection.snapshot.lines.getOrNull(line)?.elements?.getOrNull(index)
            ?: return@mapNotNull null
        val points = elementPolygon(element)?.map { matrix.map(it) ?: return@mapNotNull null }
            ?: return@mapNotNull null
        if (!validPolygon(points) || !intersectsPreview(points, overlayWidth, overlayHeight)) null
        else ElementOutline(line, index, points, ownerIds(search, line, index))
    }
    return Highlights(true, outlines)
}

private fun ownerIds(search: TextSearchResult, lineIndex: Int, elementIndex: Int): List<String> =
    search.occurrences.asSequence()
        .filter { it.lineIndex == lineIndex && elementIndex in it.elementIndices }
        .map { it.termId }
        .distinct()
        .sortedBy { id ->
            normalizeRecognizedText(search.spec.terms.firstOrNull { it.id == id }?.text.orEmpty())
                .lowercase()
        }
        .toList()

/** Sutherland-Hodgman intersection test; retain original vertices for Canvas viewport clipping. */
private fun intersectsPreview(points: List<PreviewPoint>, width: Int, height: Int): Boolean {
    var polygon = points
    for (edge in 0..3) {
        val input = polygon
        if (input.isEmpty()) return false
        polygon = buildList {
            var previous = input.last()
            fun coordinate(p: PreviewPoint) = if (edge < 2) p.x else p.y
            val limit = when (edge) { 1 -> width.toFloat(); 3 -> height.toFloat(); else -> 0f }
            fun inside(p: PreviewPoint) = if (edge % 2 == 0) coordinate(p) >= limit else coordinate(p) <= limit
            input.forEach { current ->
                if (inside(current) != inside(previous)) {
                    val t = (limit - coordinate(previous)) / (coordinate(current) - coordinate(previous))
                    add(PreviewPoint(previous.x + t * (current.x - previous.x), previous.y + t * (current.y - previous.y)))
                }
                if (inside(current)) add(current)
                previous = current
            }
        }
    }
    if (polygon.size < 3) return false
    val area = polygon.indices.sumOf { i ->
        val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
        a.x.toDouble() * b.y - b.x.toDouble() * a.y
    }
    return abs(area) > 1e-6
}
