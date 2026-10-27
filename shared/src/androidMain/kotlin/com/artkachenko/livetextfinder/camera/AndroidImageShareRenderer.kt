package com.artkachenko.livetextfinder.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.artkachenko.livetextfinder.search.ElementOutline

internal object AndroidImageShareRenderer {
    fun renderFrozen(source: Bitmap, rotation: Int, outlines: List<ElementOutline>): Bitmap {
        val upright = if (rotation == 0) {
            source
        } else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
        return try {
            drawOutlines(upright, outlines)
        } finally {
            if (upright !== source) {
                upright.recycle()
            }
        }
    }

    fun renderImported(source: Bitmap, outlines: List<ElementOutline>): Bitmap =
        drawOutlines(source, outlines)

    private fun drawOutlines(source: Bitmap, outlines: List<ElementOutline>): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        try {
            val canvas = Canvas(output)
            outlines.forEach { outline ->
                val path = android.graphics.Path().apply {
                    outline.points.forEachIndexed { index, point ->
                        if (index == 0) {
                            moveTo(point.x, point.y)
                        } else {
                            lineTo(point.x, point.y)
                        }
                    }
                    close()
                }
                canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                })
                canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.YELLOW
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                })
            }
            return output
        } catch (error: Throwable) {
            output.recycle()
            throw error
        }
    }
}
