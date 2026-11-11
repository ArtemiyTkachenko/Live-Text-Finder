package com.artkachenko.livetextfinder.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.min

/** Returns a conservative outline stroke width from a quad's minimum perpendicular span. */
internal fun highlightOutlineWidth(points: List<PreviewPoint>, maxWidth: Float): Float? {
    if (!validPolygon(points) || !maxWidth.isFinite() || maxWidth <= 0f) return null

    var minimumSpan = Double.POSITIVE_INFINITY
    points.indices.forEach { index ->
        val first = points[index]
        val second = points[(index + 1) % points.size]
        val edgeX = second.x.toDouble() - first.x.toDouble()
        val edgeY = second.y.toDouble() - first.y.toDouble()
        val edgeLength = hypot(edgeX, edgeY)
        if (!edgeLength.isFinite() || edgeLength <= 0.0) return null
        val normalX = -edgeY / edgeLength
        val normalY = edgeX / edgeLength
        var minimumProjection = Double.POSITIVE_INFINITY
        var maximumProjection = Double.NEGATIVE_INFINITY
        points.forEach { point ->
            val projection = point.x.toDouble() * normalX + point.y.toDouble() * normalY
            minimumProjection = min(minimumProjection, projection)
            maximumProjection = maxOf(maximumProjection, projection)
        }
        val span = maximumProjection - minimumProjection
        if (!span.isFinite() || span <= 0.0) return null
        minimumSpan = min(minimumSpan, span)
    }

    val width = min(maxWidth.toDouble(), minimumSpan * 0.12)
    return width.toFloat().takeIf { it.isFinite() && it > 0f }
}

internal fun DrawScope.drawSelectedOccurrenceMarker(bounds: MatchOutlineBounds) {
    val inset = 5.dp.toPx()
    val topLeft = androidx.compose.ui.geometry.Offset(bounds.left - inset, bounds.top - inset)
    val size = androidx.compose.ui.geometry.Size(
        bounds.right - bounds.left + inset * 2,
        bounds.bottom - bounds.top + inset * 2,
    )
    val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
        floatArrayOf(7.dp.toPx(), 4.dp.toPx()),
    )
    drawRect(Color.Black, topLeft, size, style = Stroke(4.dp.toPx(), pathEffect = dash))
    drawRect(Color.White, topLeft, size, style = Stroke(2.dp.toPx(), pathEffect = dash))
}

/** Draws the same paired outline used for live, frozen, and imported matches. */
internal fun DrawScope.drawHighlightOutlines(
    outlines: List<ElementOutline>,
    highlightColor: HighlightColor = HighlightColor.Yellow,
    colorsByTermId: Map<String, HighlightColor> = emptyMap(),
) {
    clipRect {
        val grouped = outlines.groupBy {
            Triple(it.lineIndex, it.elementIndex, it.points)
        }.values.mapNotNull { owners ->
            val outline = owners.firstOrNull() ?: return@mapNotNull null
            val first = outline.points.firstOrNull() ?: return@mapNotNull null
            val path = Path().apply {
                moveTo(first.x, first.y)
                outline.points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            val termIds = owners.flatMap { it.termIds }.distinct()
            outline.copy(termIds = termIds) to path
        }
        val maxOuterWidth = 2.dp.toPx()
        val strokeWidths = grouped.map { (outline, _) ->
            highlightOutlineWidth(outline.points, maxOuterWidth)
        }
        grouped.forEachIndexed { index, (_, path) ->
            val outerWidth = strokeWidths[index] ?: return@forEachIndexed
            drawPath(path, Color.Black, style = Stroke(outerWidth))
        }
        grouped.forEachIndexed { index, (outline, path) ->
            val outerWidth = strokeWidths[index] ?: return@forEachIndexed
            val foregroundWidth = outerWidth * 0.5f
            val colors = colorsByTermId.keys
                .filter { it in outline.termIds }
                .mapNotNull(colorsByTermId::get)
                .distinct()
            if (colors.size <= 1) {
                drawPath(
                    path,
                    (colors.singleOrNull() ?: highlightColor).composeColor,
                    style = Stroke(foregroundWidth),
                )
            } else {
                val segments = splitPolygonPerimeter(outline.points, colors.size)
                segments.forEachIndexed { index, points ->
                    val first = points.firstOrNull() ?: return@forEachIndexed
                    val segment = Path().apply {
                        moveTo(first.x, first.y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        segment,
                        colors[index].composeColor,
                        style = Stroke(foregroundWidth),
                    )
                }
            }
        }
    }
}

/** Splits a polygon perimeter into equal-length open segments, preserving its exact geometry. */
internal fun splitPolygonPerimeter(
    points: List<PreviewPoint>,
    count: Int,
): List<List<PreviewPoint>> {
    if (points.size < 2 || count <= 1) return listOf(points + listOfNotNull(points.firstOrNull()))
    val closed = points + points.first()
    val lengths = closed.zipWithNext { first, second ->
        kotlin.math.hypot(
            (second.x - first.x).toDouble(),
            (second.y - first.y).toDouble(),
        ).toFloat()
    }
    val total = lengths.sum()
    if (!total.isFinite() || total <= 0f) return emptyList()
    return (0 until count).map { index ->
        val start = total * index / count
        val end = total * (index + 1) / count
        val result = mutableListOf<PreviewPoint>()
        var distance = 0f
        lengths.forEachIndexed { edge, length ->
            val edgeStart = distance
            val edgeEnd = distance + length
            if (length > 0f && edgeEnd > start && edgeStart < end) {
                val from = ((start - edgeStart) / length).coerceIn(0f, 1f)
                val to = ((end - edgeStart) / length).coerceIn(0f, 1f)
                val a = closed[edge]
                val b = closed[edge + 1]
                val p1 = PreviewPoint(a.x + (b.x - a.x) * from, a.y + (b.y - a.y) * from)
                val p2 = PreviewPoint(a.x + (b.x - a.x) * to, a.y + (b.y - a.y) * to)
                if (result.lastOrNull() != p1) result += p1
                result += p2
            }
            distance = edgeEnd
        }
        result
    }
}
