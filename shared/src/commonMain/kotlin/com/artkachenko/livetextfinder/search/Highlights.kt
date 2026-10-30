package com.artkachenko.livetextfinder.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.camera.FrozenFrameFit
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.ImportedImageState
import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.TextElement
import kotlin.math.abs

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
        grouped.forEach { (_, path) ->
            drawPath(path, Color.Black, style = Stroke(6.dp.toPx()))
        }
        grouped.forEach { (outline, path) ->
            val colors = colorsByTermId.keys
                .filter { it in outline.termIds }
                .mapNotNull(colorsByTermId::get)
                .distinct()
            if (colors.size <= 1) {
                drawPath(
                    path,
                    (colors.singleOrNull() ?: highlightColor).composeColor,
                    style = Stroke(3.dp.toPx()),
                )
            } else {
                val segments = splitPolygonPerimeter(outline.points, colors.size)
                segments.forEachIndexed { index, points ->
                    val first = points.firstOrNull() ?: return@forEachIndexed
                    val segment = Path().apply {
                        moveTo(first.x, first.y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(segment, colors[index].composeColor, style = Stroke(3.dp.toPx()))
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

/** Full-buffer upright → raw → sensor → actual view. Crop is validated, never subtracted twice. */
internal fun uprightToPreview(frame: FrameInfo, target: PreviewTarget): PixelTransform? {
    if (frame.bufferWidth <= 0 || frame.bufferHeight <= 0 || target.width <= 0 || target.height <= 0 ||
        frame.rotationDegrees !in listOf(0, 90, 180, 270) || frame.crop.left < 0 || frame.crop.top < 0 ||
        frame.crop.right > frame.bufferWidth || frame.crop.bottom > frame.bufferHeight ||
        frame.crop.right <= frame.crop.left || frame.crop.bottom <= frame.crop.top
    ) return null
    val sensorToBuffer = PixelTransform.from(frame.sensorToBufferMatrix) ?: return null
    val sensorToView = PixelTransform.from(target.sensorToView) ?: return null
    // Validate target invertibility as well: singular targets must never draw an identity fallback.
    if (sensorToView.inverse() == null) return null
    val w = frame.bufferWidth.toDouble()
    val h = frame.bufferHeight.toDouble()
    val uprightToRaw = PixelTransform(when (frame.rotationDegrees) {
        90 -> listOf(0.0, 1.0, 0.0, -1.0, 0.0, h, 0.0, 0.0, 1.0)
        180 -> listOf(-1.0, 0.0, w, 0.0, -1.0, h, 0.0, 0.0, 1.0)
        270 -> listOf(0.0, -1.0, w, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        else -> listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
    })
    return sensorToView * (sensorToBuffer.inverse() ?: return null) * uprightToRaw
}

/** Small immutable 3×3 helper for tested shared geometry, never an Android Matrix object. */
internal class PixelTransform(private val values: List<Double>) {
    operator fun times(other: PixelTransform) = PixelTransform(List(9) { i ->
        val row = i / 3
        val col = i % 3
        (0..2).sumOf { k -> values[row * 3 + k] * other.values[k * 3 + col] }
    })

    fun inverse(): PixelTransform? {
        val (a, b, c) = values
        val d = values[3]; val e = values[4]; val f = values[5]
        val g = values[6]; val h = values[7]; val i = values[8]
        val determinant = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        if (!determinant.isFinite() || abs(determinant) < 1e-12) return null
        val inverse = listOf(e * i - f * h, c * h - b * i, b * f - c * e,
            f * g - d * i, a * i - c * g, c * d - a * f,
            d * h - e * g, b * g - a * h, a * e - b * d).map { it / determinant }
        return if (inverse.all { it.isFinite() }) PixelTransform(inverse) else null
    }

    fun map(point: PreviewPoint): PreviewPoint? {
        val x = point.x.toDouble(); val y = point.y.toDouble()
        val z = values[6] * x + values[7] * y + values[8]
        if (!z.isFinite() || abs(z) < 1e-9) return null
        val px = ((values[0] * x + values[1] * y + values[2]) / z).toFloat()
        val py = ((values[3] * x + values[4] * y + values[5]) / z).toFloat()
        return if (px.isFinite() && py.isFinite()) PreviewPoint(px, py) else null
    }

    companion object {
        fun from(values: List<Float>): PixelTransform? = if (values.size == 9 && values.all { it.isFinite() } && values[6] == 0f && values[7] == 0f) {
            PixelTransform(values.map { it.toDouble() })
        } else null
    }
}

internal fun elementPolygon(element: TextElement): List<PreviewPoint>? {
    val corners = element.corners.map { PreviewPoint(it.x.toFloat(), it.y.toFloat()) }
    if (validPolygon(corners)) return corners
    val bounds = element.bounds ?: return null
    if (bounds.right <= bounds.left || bounds.bottom <= bounds.top) return null
    return listOf(PreviewPoint(bounds.left.toFloat(), bounds.top.toFloat()),
        PreviewPoint(bounds.right.toFloat(), bounds.top.toFloat()),
        PreviewPoint(bounds.right.toFloat(), bounds.bottom.toFloat()),
        PreviewPoint(bounds.left.toFloat(), bounds.bottom.toFloat()))
}

/** Requires an ordered convex quadrilateral; self-crossing/duplicate/collinear corners fall back. */
internal fun validPolygon(points: List<PreviewPoint>): Boolean {
    if (points.size != 4 || points.any { !it.x.isFinite() || !it.y.isFinite() }) return false
    val turns = points.indices.map { j ->
        val a = points[j]; val b = points[(j + 1) % 4]; val c = points[(j + 2) % 4]
        (b.x.toDouble() - a.x) * (c.y - b.y) - (b.y.toDouble() - a.y) * (c.x - b.x)
    }
    return turns.all { it > 1e-6 } || turns.all { it < -1e-6 }
}

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
