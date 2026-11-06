package com.artkachenko.livetextfinder.search

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.artkachenko.livetextfinder.camera.AndroidImageShareRenderer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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

            assertEquals(highlightColor.argb.toInt(), bitmap.getPixel(62, 50))
            assertEquals(0, bitmap.getPixel(15, 50))
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

            assertForegroundContribution(decoded.getPixel(62, 50), highlightColor.argb.toInt())
            assertEquals(AndroidColor.RED, decoded.getPixel(17, 50))
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
    fun thinOutlinePreservesTextLikeInteriorPixelsInComposeAndExportPng() {
        val outline = ElementOutline(0, 0, rectangle(20f, 20f, 100f, 26f))
        val composeBitmap = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        composeBitmap.eraseColor(AndroidColor.WHITE)
        CanvasDrawScope().draw(
            density = Density(2f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(composeBitmap.asImageBitmap()),
            size = Size(120f, 60f),
        ) {
            drawRect(
                Color.Black,
                topLeft = androidx.compose.ui.geometry.Offset(50f, 21f),
                size = Size(1f, 4f),
            )
            drawRect(
                Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(51f, 22f),
                size = Size(2f, 2f),
            )
            drawHighlightOutlines(listOf(outline), HighlightColor.Magenta)
        }
        assertEquals(AndroidColor.BLACK, composeBitmap.getPixel(50, 23))
        assertEquals(AndroidColor.WHITE, composeBitmap.getPixel(51, 23))
        val composeEdge = composeBitmap.getPixel(60, 20)
        assertTrue(AndroidColor.red(composeEdge) > AndroidColor.green(composeEdge))
        assertTrue(AndroidColor.blue(composeEdge) > AndroidColor.green(composeEdge))
        writeVisualOutput("thin-outline-compose-density-2.png", composeBitmap)
        composeBitmap.recycle()

        val source = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        source.eraseColor(AndroidColor.WHITE)
        android.graphics.Canvas(source).apply {
            drawRect(50f, 21f, 51f, 25f, android.graphics.Paint().apply {
                color = AndroidColor.BLACK
            })
            drawRect(51f, 22f, 53f, 24f, android.graphics.Paint().apply {
                color = AndroidColor.WHITE
            })
        }
        val rendered = AndroidImageShareRenderer.renderImported(
            source,
            listOf(outline),
            HighlightColor.Magenta,
        )
        val encoded = java.io.ByteArrayOutputStream()
        rendered.compress(Bitmap.CompressFormat.PNG, 100, encoded)
        val decoded = BitmapFactory.decodeByteArray(encoded.toByteArray(), 0, encoded.size())
        assertEquals(AndroidColor.BLACK, decoded.getPixel(50, 23))
        assertEquals(AndroidColor.WHITE, decoded.getPixel(51, 23))
        val exportedEdge = decoded.getPixel(60, 20)
        assertTrue(AndroidColor.red(exportedEdge) > AndroidColor.green(exportedEdge))
        assertTrue(AndroidColor.blue(exportedEdge) > AndroidColor.green(exportedEdge))
        writeVisualOutput("thin-outline-export.png", decoded)
        decoded.recycle()
        rendered.recycle()
        source.recycle()
    }

    @Test
    fun laterBlackUnderstrokeDoesNotCoverEarlierForegroundAtOverlappingEdges() {
        val first = HighlightColor.fromRgb(220, 30, 40)
        val second = HighlightColor.fromRgb(20, 190, 90)
        val overlapping = listOf(
            ElementOutline(0, 0, rectangle(20f, 20f, 62f, 80f), listOf("first")),
            ElementOutline(0, 1, rectangle(60f, 20f, 100f, 80f), listOf("second")),
        )
        val bitmap = Bitmap.createBitmap(120, 100, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            density = Density(2f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap.asImageBitmap()),
            size = Size(120f, 100f),
        ) {
            drawHighlightOutlines(
                overlapping,
                HighlightColor.Yellow,
                mapOf("first" to first, "second" to second),
            )
        }
        val edge = bitmap.getPixel(61, 50)
        val reference = Bitmap.createBitmap(120, 100, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            density = Density(2f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(reference.asImageBitmap()),
            size = Size(120f, 100f),
        ) {
            drawHighlightOutlines(listOf(overlapping.first()), first)
        }
        assertEquals(reference.getPixel(61, 50), edge)
        reference.recycle()
        bitmap.recycle()

        val pngSource = Bitmap.createBitmap(120, 100, Bitmap.Config.ARGB_8888)
        pngSource.eraseColor(AndroidColor.WHITE)
        val pngOutlines = listOf(
            ElementOutline(0, 0, rectangle(20f, 20f, 62.5f, 80f), listOf("first")),
            ElementOutline(0, 1, rectangle(61.5f, 20f, 100f, 80f), listOf("second")),
        )
        val png = AndroidImageShareRenderer.renderImported(
            pngSource,
            pngOutlines,
            HighlightColor.Yellow,
            mapOf("first" to first, "second" to second),
        )
        val encoded = java.io.ByteArrayOutputStream()
        png.compress(Bitmap.CompressFormat.PNG, 100, encoded)
        val decoded = BitmapFactory.decodeByteArray(encoded.toByteArray(), 0, encoded.size())
        val pngEdge = decoded.getPixel(62, 50)
        assertEquals(first.argb.toInt(), pngEdge)
        writeVisualOutput("thin-outline-png-overlap.png", decoded)
        decoded.recycle()
        png.recycle()
        pngSource.recycle()
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
        assertForegroundContribution(bitmap.getPixel(30, 20), first.argb.toInt())
        assertForegroundContribution(bitmap.getPixel(45, 80), second.argb.toInt())
        assertEquals(0, bitmap.getPixel(17, 50))
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
        assertForegroundContribution(decoded.getPixel(30, 20), first.argb.toInt())
        assertForegroundContribution(decoded.getPixel(45, 80), second.argb.toInt())
        assertEquals(AndroidColor.WHITE, decoded.getPixel(17, 50))
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
        assertForegroundContribution(bitmap.getPixel(30, 10), first.argb.toInt())
        assertForegroundContribution(bitmap.getPixel(80, 10), second.argb.toInt())
        bitmap.recycle()
    }

    private fun assertForegroundContribution(actual: Int, expected: Int) {
        fun matchesExpectedBlend(pixel: Int): Boolean {
            val expectedChannels = intArrayOf(
                AndroidColor.red(expected),
                AndroidColor.green(expected),
                AndroidColor.blue(expected),
            )
            val actualChannels = intArrayOf(
                AndroidColor.red(pixel),
                AndroidColor.green(pixel),
                AndroidColor.blue(pixel),
            )
            return actualChannels.indices.all { channel ->
                kotlin.math.abs(actualChannels[channel] - expectedChannels[channel] * 0.5) <= 8.0
            }
        }

        assertTrue(matchesExpectedBlend(actual), "Expected half-color blend $expected, got $actual")
        assertFalse(matchesExpectedBlend(AndroidColor.BLACK), "A missing foreground must fail")
        if (expected == HighlightColor.fromRgb(18, 171, 239).argb.toInt()) {
            assertFalse(
                matchesExpectedBlend(AndroidColor.rgb(0, 127, 127)),
                "A wrong cyan foreground must fail",
            )
        }
    }

    private fun writeVisualOutput(name: String, bitmap: Bitmap) {
        val outputDirectory = System.getenv("LTF_VISUAL_OUTPUT_DIR") ?: return
        val directory = File(outputDirectory)
        directory.mkdirs()
        File(directory, name).outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun rectangle(left: Float, top: Float, right: Float, bottom: Float) = listOf(
        PreviewPoint(left, top),
        PreviewPoint(right, top),
        PreviewPoint(right, bottom),
        PreviewPoint(left, bottom),
    )
}
