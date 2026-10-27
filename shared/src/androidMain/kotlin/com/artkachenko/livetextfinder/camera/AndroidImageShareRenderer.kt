package com.artkachenko.livetextfinder.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.artkachenko.livetextfinder.search.ElementOutline
import com.artkachenko.livetextfinder.search.HighlightColor

internal object AndroidImageShareRenderer {
    fun renderFrozen(
        source: Bitmap,
        rotation: Int,
        outlines: List<ElementOutline>,
        highlightColor: HighlightColor = HighlightColor.Yellow,
    ): Bitmap {
        val upright = if (rotation == 0) {
            source
        } else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
        return try {
            drawOutlines(upright, outlines, highlightColor)
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
    ): Bitmap = drawOutlines(source, outlines, highlightColor)

    private fun drawOutlines(
        source: Bitmap,
        outlines: List<ElementOutline>,
        highlightColor: HighlightColor,
    ): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        try {
            val canvas = Canvas(output)
            val paths = outlines.mapNotNull { outline ->
                val first = outline.points.firstOrNull() ?: return@mapNotNull null
                android.graphics.Path().apply {
                    moveTo(first.x, first.y)
                    outline.points.drop(1).forEach { point ->
                        lineTo(point.x, point.y)
                    }
                    close()
                }
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
            paths.forEach { path ->
                canvas.drawPath(path, blackPaint)
            }
            paths.forEach { path ->
                canvas.drawPath(path, highlightPaint)
            }
            return output
        } catch (error: Throwable) {
            output.recycle()
            throw error
        }
    }
}
