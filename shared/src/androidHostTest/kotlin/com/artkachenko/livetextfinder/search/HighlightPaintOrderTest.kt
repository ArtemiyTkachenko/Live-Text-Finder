package com.artkachenko.livetextfinder.search

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.artkachenko.livetextfinder.camera.AndroidImageShareRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Checks overlapping production outline renderers on native host graphics and encoded PNGs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HighlightPaintOrderTest {
    private val outlines = listOf(
        ElementOutline(0, 0, rectangle(20f, 20f, 62f, 80f)),
        ElementOutline(0, 1, rectangle(60f, 20f, 100f, 80f)),
    )

    @Test
    fun nativeCanvasKeepsForegroundEdgesVisibleAcrossAllPaletteColorsAtHigherDensity() {
        val colors = HighlightColor.entries + HighlightColor.fromRgb(18, 171, 239)
        colors.forEach { highlightColor ->
            val bitmap = Bitmap.createBitmap(120, 100, Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(
                density = Density(2f),
                layoutDirection = LayoutDirection.Ltr,
                canvas = Canvas(bitmap.asImageBitmap()),
                size = Size(120f, 100f),
            ) {
                drawHighlightOutlines(outlines, highlightColor)
            }

            assertEquals(highlightColor.argb.toInt(), bitmap.getPixel(64, 50))
            assertEquals(AndroidColor.BLACK, bitmap.getPixel(15, 50))
            bitmap.recycle()
        }
    }

    @Test
    fun encodedPngKeepsForegroundEdgesVisibleAcrossAllPaletteColors() {
        val colors = HighlightColor.entries + HighlightColor.fromRgb(18, 171, 239)
        colors.forEach { highlightColor ->
            val source = Bitmap.createBitmap(120, 100, Bitmap.Config.ARGB_8888)
            source.eraseColor(AndroidColor.RED)
            val rendered = AndroidImageShareRenderer.renderImported(
                source,
                outlines,
                highlightColor,
            )
            val encoded = java.io.ByteArrayOutputStream()
            rendered.compress(Bitmap.CompressFormat.PNG, 100, encoded)
            val decoded = BitmapFactory.decodeByteArray(encoded.toByteArray(), 0, encoded.size())

            assertEquals(highlightColor.argb.toInt(), decoded.getPixel(62, 50))
            assertEquals(AndroidColor.BLACK, decoded.getPixel(17, 50))
            decoded.recycle()
            rendered.recycle()
            source.recycle()
        }
    }

    @Test
    fun isolatedNativeQuadKeepsAllFourForegroundEdgesClosed() {
        val bitmap = Bitmap.createBitmap(120, 100, Bitmap.Config.ARGB_8888)
        val outline = ElementOutline(0, 0, rectangle(30f, 30f, 90f, 70f))
        CanvasDrawScope().draw(
            density = Density(2f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap.asImageBitmap()),
            size = Size(120f, 100f),
        ) {
            drawHighlightOutlines(listOf(outline), HighlightColor.Magenta)
        }

        listOf(60 to 30, 90 to 50, 60 to 70, 30 to 50).forEach { (x, y) ->
            assertEquals(HighlightColor.Magenta.argb.toInt(), bitmap.getPixel(x, y))
        }
        bitmap.recycle()
    }

    @Test
    fun coincidentOwnersKeepBothColorsAfterBlackUnderstrokeInNativeAndPngRendering() {
        val first = HighlightColor.fromRgb(220, 30, 40)
        val second = HighlightColor.fromRgb(20, 190, 90)
        val owners = listOf(
            ElementOutline(0, 0, rectangle(20f, 20f, 62f, 80f), listOf("first")),
            ElementOutline(0, 0, rectangle(20f, 20f, 62f, 80f), listOf("second")),
        )
        val colors = mapOf("first" to first, "second" to second)
        val bitmap = Bitmap.createBitmap(120, 100, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap.asImageBitmap()),
            size = Size(120f, 100f),
        ) {
            drawHighlightOutlines(owners, HighlightColor.Yellow, colors)
        }
        assertEquals(first.argb.toInt(), bitmap.getPixel(30, 20))
        assertEquals(second.argb.toInt(), bitmap.getPixel(45, 80))
        assertEquals(AndroidColor.BLACK, bitmap.getPixel(17, 50))
        bitmap.recycle()

        val source = Bitmap.createBitmap(120, 100, Bitmap.Config.ARGB_8888)
        source.eraseColor(AndroidColor.WHITE)
        val rendered = AndroidImageShareRenderer.renderImported(
            source,
            owners,
            HighlightColor.Yellow,
            colors,
        )
        val encoded = java.io.ByteArrayOutputStream()
        rendered.compress(Bitmap.CompressFormat.PNG, 100, encoded)
        val decoded = BitmapFactory.decodeByteArray(encoded.toByteArray(), 0, encoded.size())
        assertEquals(first.argb.toInt(), decoded.getPixel(30, 20))
        assertEquals(second.argb.toInt(), decoded.getPixel(45, 80))
        assertEquals(AndroidColor.BLACK, decoded.getPixel(17, 50))
        decoded.recycle()
        rendered.recycle()
        source.recycle()
    }

    @Test
    fun sameElementIndexWithDifferentRetainedGeometryIsNotMerged() {
        val first = HighlightColor.fromRgb(220, 30, 40)
        val second = HighlightColor.fromRgb(20, 190, 90)
        val outlines = listOf(
            ElementOutline(0, 0, rectangle(10f, 10f, 50f, 50f), listOf("first")),
            ElementOutline(0, 0, rectangle(60f, 10f, 100f, 50f), listOf("second")),
        )
        val bitmap = Bitmap.createBitmap(120, 100, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap.asImageBitmap()),
            size = Size(120f, 100f),
        ) {
            drawHighlightOutlines(
                outlines,
                HighlightColor.Yellow,
                mapOf("first" to first, "second" to second),
            )
        }
        assertEquals(first.argb.toInt(), bitmap.getPixel(30, 10))
        assertEquals(second.argb.toInt(), bitmap.getPixel(80, 10))
        bitmap.recycle()
    }

    private fun rectangle(left: Float, top: Float, right: Float, bottom: Float) = listOf(
        PreviewPoint(left, top),
        PreviewPoint(right, top),
        PreviewPoint(right, bottom),
        PreviewPoint(left, bottom),
    )
}
