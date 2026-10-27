package com.artkachenko.livetextfinder.camera

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import com.artkachenko.livetextfinder.ocr.TextPoint
import com.artkachenko.livetextfinder.search.drawHighlightOutlines
import com.artkachenko.livetextfinder.search.importedHighlightsForDraw
import com.artkachenko.livetextfinder.search.manualSearchSpec
import com.artkachenko.livetextfinder.search.searchRecognizedContent
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises decoded pixels, adoption, rendering and match outlines on host graphics. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportedImageRenderTest {
    @Test
    fun decodedAdoptedStillAndMatchingOutlinePaintAndRespectStaleVisibilityGuards() {
        val source = asymmetricBitmap()
        val output = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.PNG, 100, output)
        val encoded = output.toByteArray()
        source.recycle()
        val decoded = ImportedImageDecoder().decode(ByteArrayInputStream(encoded))
        val owner = ImportedImageOwner()
        val ticket = checkNotNull(owner.beginPicker())
        val snapshot = ImportedTextSnapshot(
            text = "target",
            lines = listOf(
                TextLine(
                    "target",
                    null,
                    emptyList(),
                    listOf(
                        TextElement(
                            "target",
                            TextBounds(20, 12, 60, 30),
                            listOf(
                                TextPoint(20, 12), TextPoint(60, 12),
                                TextPoint(60, 30), TextPoint(20, 30),
                            ),
                        ),
                    ),
                ),
            ),
            width = decoded.width,
            height = decoded.height,
            sourceIdentity = 44L,
        )
        assertTrue(owner.adoptProcessedImage(ticket, ProcessedImportedImage(decoded, snapshot)))
        val search = searchRecognizedContent(manualSearchSpec("target"), snapshot)
        val drawProjection = importedHighlightsForDraw(
            search,
            snapshot,
            owner.state.value,
            search.spec.identity,
            search.spec.identity,
            80f,
            48f,
            true,
        )
        val outlines = drawProjection.outlines
        assertEquals(1, outlines.size)

        val visible = render(owner, search, 44L, decoded, 80, 48)
        assertEquals(Color.RED, visible.getPixel(10, 10))
        assertEquals(Color.YELLOW, visible.getPixel(40, 12))
        val hidden = render(owner, search, 44L, decoded, 80, 48, isVisible = { false })
        assertTransparent(hidden)
        assertSame(decoded, owner.currentBitmap())
        val staleSource = render(owner, search, 45L, decoded, 80, 48)
        assertTransparent(staleSource)
        val portrait = render(owner, search, 44L, decoded, 48, 80)
        assertEquals(Color.RED, portrait.getPixel(6, 32))
        assertEquals(Color.YELLOW, portrait.getPixel(24, 33))

        owner.cancel()
        val afterCancel = render(owner, search, 44L, decoded, 80, 48)
        assertTransparent(afterCancel)
        assertFalse(decoded.isRecycled)
        assertEquals(ImportedImagePhase.Live, owner.state.value.phase)
        owner.dispose()
        listOf(visible, hidden, staleSource, portrait, afterCancel, decoded)
            .forEach(Bitmap::recycle)
    }

    private fun render(
        owner: ImportedImageOwner,
        search: com.artkachenko.livetextfinder.search.TextSearchResult,
        sourceIdentity: Long,
        sourceBitmap: Bitmap,
        width: Int,
        height: Int,
        isVisible: () -> Boolean = { true },
    ): Bitmap {
        val target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(target.asImageBitmap()),
            size = Size(width.toFloat(), height.toFloat()),
        ) {
            drawImportedBitmap(owner, sourceIdentity, sourceBitmap, isVisible)
            val drawProjection = importedHighlightsForDraw(
                search,
                owner.state.value.snapshot,
                owner.state.value,
                search.spec.identity,
                search.spec.identity,
                size.width,
                size.height,
                isVisible(),
            )
            if (drawProjection.ready &&
                drawProjection.outlines.isNotEmpty() &&
                owner.state.value.snapshot?.sourceIdentity == sourceIdentity
            ) {
                drawHighlightOutlines(drawProjection.outlines)
            }
        }
        return target
    }

    private fun asymmetricBitmap(): Bitmap = Bitmap.createBitmap(80, 48, Bitmap.Config.ARGB_8888)
        .apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setPixel(
                        x,
                        y,
                        when {
                            x < 40 && y < 24 -> Color.RED
                            x >= 40 && y < 24 -> Color.GREEN
                            x < 40 -> Color.BLUE
                            else -> Color.YELLOW
                        },
                    )
                }
            }
        }

    private fun assertTransparent(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(pixels.all { it == Color.TRANSPARENT })
    }
}
