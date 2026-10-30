package com.artkachenko.livetextfinder.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.artkachenko.livetextfinder.search.ElementOutline
import com.artkachenko.livetextfinder.search.HighlightColor
import com.artkachenko.livetextfinder.search.splitPolygonPerimeter

internal object AndroidImageShareRenderer {
    fun renderFrozen(
        source: Bitmap,
        rotation: Int,
        outlines: List<ElementOutline>,
        highlightColor: HighlightColor = HighlightColor.Yellow,
        colorsByTermId: Map<String, HighlightColor> = emptyMap(),
    ): Bitmap {
        val upright = if (rotation == 0) {
            source
        } else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
        return try {
            drawOutlines(upright, outlines, highlightColor, colorsByTermId)
        } finally {
            if (upright !== source) {
                upright.recycle()
            }
        }
    }

    fun renderImported(
        source: Bitmap,
        outlines: List<ElementOutline>,
        highlightColor: HighlightColor = HighlightColor.Yellow,
        colorsByTermId: Map<String, HighlightColor> = emptyMap(),
    ): Bitmap = drawOutlines(source, outlines, highlightColor, colorsByTermId)

    private fun drawOutlines(
        source: Bitmap,
        outlines: List<ElementOutline>,
        highlightColor: HighlightColor,
        colorsByTermId: Map<String, HighlightColor>,
    ): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        try {
            val canvas = Canvas(output)
            val grouped = outlines.groupBy {
                Triple(it.lineIndex, it.elementIndex, it.points)
            }.values.mapNotNull { owners ->
                val outline = owners.firstOrNull() ?: return@mapNotNull null
                val first = outline.points.firstOrNull() ?: return@mapNotNull null
                val path = android.graphics.Path().apply {
                    moveTo(first.x, first.y)
                    outline.points.drop(1).forEach { point ->
                        lineTo(point.x, point.y)
                    }
                    close()
                }
                outline.copy(termIds = owners.flatMap { it.termIds }.distinct()) to path
            }
            val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }
            val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = highlightColor.argb.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            grouped.forEach { (_, path) ->
                canvas.drawPath(path, blackPaint)
            }
            grouped.forEach { (outline, path) ->
                val colors = colorsByTermId.keys
                    .filter { it in outline.termIds }
                    .mapNotNull(colorsByTermId::get)
                    .distinct()
                if (colors.size <= 1) {
                    highlightPaint.color = (colors.singleOrNull() ?: highlightColor).argb.toInt()
                    canvas.drawPath(path, highlightPaint)
                } else {
                    val segments = splitPolygonPerimeter(outline.points, colors.size)
                    segments.forEachIndexed { index, points ->
                        val first = points.firstOrNull() ?: return@forEachIndexed
                        val segment = android.graphics.Path().apply {
                            moveTo(first.x, first.y)
                            points.drop(1).forEach { point -> lineTo(point.x, point.y) }
                        }
                        highlightPaint.color = colors[index].argb.toInt()
                        canvas.drawPath(segment, highlightPaint)
                    }
                }
            }
            return output
        } catch (error: Throwable) {
            output.recycle()
            throw error
        }
    }
}
