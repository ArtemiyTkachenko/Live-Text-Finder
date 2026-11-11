package com.artkachenko.livetextfinder.search

import com.artkachenko.livetextfinder.ocr.OcrSnapshot











/** Android Matrix value order, column-vector points; units are preview-local pixels. */
internal data class PreviewTarget(val width: Int, val height: Int, val sensorToView: List<Float>)
internal data class FrameIdentity(val session: Long, val generation: Long)
internal data class FrameProjection(
    val snapshot: OcrSnapshot,
    val identity: FrameIdentity,
    val target: PreviewTarget,
)
internal data class PreviewPoint(val x: Float, val y: Float)
internal data class ElementOutline(
    val lineIndex: Int,
    val elementIndex: Int,
    val points: List<PreviewPoint>,
    val termIds: List<String> = emptyList(),
)
internal data class Highlights(val ready: Boolean, val outlines: List<ElementOutline>)

internal data class MatchOutlineBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal fun occurrenceBounds(
    occurrence: TextOccurrence,
    highlights: Highlights,
): MatchOutlineBounds? {
    if (!highlights.ready || occurrence.elementIndices.isEmpty()) return null
    val selected = occurrence.elementIndices.distinct().map { elementIndex ->
        highlights.outlines.singleOrNull {
            it.lineIndex == occurrence.lineIndex && it.elementIndex == elementIndex
        } ?: return null
    }
    if (selected.any { outline ->
        !validPolygon(outline.points)
    }) return null
    val points = selected.flatMap { it.points }
    return MatchOutlineBounds(
        points.minOf { it.x },
        points.minOf { it.y },
        points.maxOf { it.x },
        points.maxOf { it.y },
    ).takeIf { it.right > it.left && it.bottom > it.top }
}
