package com.artkachenko.livetextfinder.camera

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.artkachenko.livetextfinder.ocr.CapturedCameraFrame
import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import com.artkachenko.livetextfinder.ocr.TextPoint
import com.artkachenko.livetextfinder.search.FrameIdentity
import com.artkachenko.livetextfinder.search.HighlightColor
import com.artkachenko.livetextfinder.search.drawHighlightOutlines
import com.artkachenko.livetextfinder.search.manualSearchSpec
import com.artkachenko.livetextfinder.search.projectFrozenHighlights
import com.artkachenko.livetextfinder.search.searchRecognizedText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Paints through the renderer used by FrozenBitmapPreview, using real host graphics. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrozenBitmapRenderTest {
    @Test
    fun productionRendererRotatesAsymmetricPixelsAndRefitsAfterViewportResize() {
        val wide = mapOf(
            0 to listOf(30 to 22, 90 to 22, 30 to 58, 90 to 58),
            90 to listOf(72 to 20, 72 to 60, 48 to 20, 48 to 60),
            180 to listOf(90 to 58, 30 to 58, 90 to 22, 30 to 22),
            270 to listOf(48 to 60, 48 to 20, 72 to 60, 72 to 20),
        )
        val square = mapOf(
            0 to listOf(16 to 22, 48 to 22, 16 to 42, 48 to 42),
            90 to listOf(42 to 16, 42 to 48, 22 to 16, 22 to 48),
            180 to listOf(48 to 42, 16 to 42, 48 to 22, 16 to 22),
            270 to listOf(22 to 48, 22 to 16, 42 to 48, 42 to 16),
        )
        wide.forEach { (rotation, expectedWide) ->
            val pixels = asymmetricBitmap()
            val owner = frozenOwner(pixels, rotation)
            val frame = assertNotNull(owner.state.value.frame)
            val first = render(owner, frame, pixels, 64, 64)
            val resized = render(owner, frame, pixels, 120, 80)

            assertLandmarks(first, square.getValue(rotation))
            assertLandmarks(resized, expectedWide)
            assertEquals(Color.TRANSPARENT, first.getPixel(0, 0))
            assertEquals(Color.TRANSPARENT, resized.getPixel(0, 0))
            owner.stop()
            first.recycle()
            resized.recycle()
            pixels.recycle()
        }
    }

    @Test
    fun visibilityIsReadWhenPaintingAndHiddenRendererPreservesFrozenOwner() {
        val pixels = asymmetricBitmap()
        val owner = frozenOwner(pixels, 90)
        val frame = assertNotNull(owner.state.value.frame)
        var visible = true
        val visibility = { visible }
        visible = false

        val hidden = render(owner, frame, pixels, 120, 80, visibility)

        assertTransparent(hidden)
        assertEquals(FreezeMode.Frozen, owner.state.value.mode)
        visible = true
        val shown = render(owner, frame, pixels, 120, 80, visibility)
        assertEquals(Color.RED, shown.getPixel(72, 20))
        owner.stop()
        hidden.recycle()
        shown.recycle()
        pixels.recycle()
    }

    @Test
    fun oldDrawClosureCannotPaintAfterResumeOrWithUnownedBitmap() {
        val pixels = asymmetricBitmap()
        val owner = frozenOwner(pixels, 0)
        val frame = assertNotNull(owner.state.value.frame)
        val otherPixels = asymmetricBitmap()
        val wrongBitmap = render(owner, frame, otherPixels, 120, 80)
        assertTransparent(wrongBitmap)
        owner.resume()

        val oldClosure = render(owner, frame, pixels, 120, 80)

        assertTransparent(oldClosure)
        owner.stop()
        wrongBitmap.recycle()
        oldClosure.recycle()
        otherPixels.recycle()
        pixels.recycle()
    }

    @Test
    fun frozenPreviewDrawsEverySelectedColorOverRetainedFrame() {
        val pixels = asymmetricBitmap()
        val owner = frozenOwner(pixels, 0, includeTarget = true)
        val frame = assertNotNull(owner.state.value.frame)
        val colors = HighlightColor.entries + HighlightColor.fromRgb(18, 171, 239)
        colors.forEach { highlightColor ->
            val preview = render(
                owner,
                frame,
                pixels,
                80,
                48,
                highlightColor = highlightColor,
            )
            assertEquals(Color.RED, preview.getPixel(1, 1))
            assertEquals(highlightColor.argb.toInt(), preview.getPixel(40, 12))
            assertEquals(Color.BLACK, preview.getPixel(17, 20))
            preview.recycle()
        }
        owner.stop()
        pixels.recycle()
    }

    private fun render(
        owner: FrozenFrameOwner,
        frame: FrozenSearchFrame,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        visible: () -> Boolean = { true },
        highlightColor: HighlightColor? = null,
    ): Bitmap {
        val target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(target.asImageBitmap()),
            size = Size(width.toFloat(), height.toFloat()),
        ) {
            drawFrozenBitmap(owner, frame, bitmap, visible)
            if (highlightColor != null && visible()) {
                val search = searchRecognizedText(
                    manualSearchSpec("target"),
                    OcrUiState(OcrStatus.TextRecognized, snapshot = frame.snapshot),
                )
                val outlines = projectFrozenHighlights(search, size.width, size.height).outlines
                drawHighlightOutlines(outlines, highlightColor)
            }
        }
        return target
    }

    private fun assertLandmarks(bitmap: Bitmap, points: List<Pair<Int, Int>>) {
        val expectedColors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        points.zip(expectedColors).forEach { (point, expected) ->
            assertEquals(expected, bitmap.getPixel(point.first, point.second))
        }
    }

    private fun assertTransparent(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(pixels.all { it == Color.TRANSPARENT })
    }

    private fun asymmetricBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(80, 48, Bitmap.Config.ARGB_8888)
        val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        val pixels = IntArray(80 * 48) { index ->
            val x = index % 80
            val y = index / 80
            colors[(if (y < 24) 0 else 2) + if (x < 40) 0 else 1]
        }
        bitmap.setPixels(pixels, 0, 80, 0, 0, 80, 48)
        return bitmap
    }

    private fun frozenOwner(
        bitmap: Bitmap,
        rotation: Int,
        includeTarget: Boolean = false,
    ): FrozenFrameOwner {
        val owner = FrozenFrameOwner()
        var requestId = 0L
        owner.attachSession(
            requestCapture = {
                requestId = it
                true
            },
            cancelCapture = {
            },
        )
        assertTrue(owner.freeze(cameraReady = true))
        assertTrue(owner.accept(CapturedCameraFrame(
            requestId = requestId,
            bitmap = bitmap,
            snapshot = OcrSnapshot(
                text = if (includeTarget) "target" else "",
                lines = if (includeTarget) {
                    listOf(
                        TextLine(
                            "target",
                            null,
                            emptyList(),
                            listOf(
                                TextElement(
                                    "target",
                                    TextBounds(20, 12, 60, 30),
                                    listOf(
                                        TextPoint(20, 12),
                                        TextPoint(60, 12),
                                        TextPoint(60, 30),
                                        TextPoint(20, 30),
                                    ),
                                ),
                            ),
                        ),
                    )
                } else {
                    emptyList()
                },
                frame = FrameInfo(
                    timestampNanos = 42L,
                    bufferWidth = bitmap.width,
                    bufferHeight = bitmap.height,
                    rotationDegrees = rotation,
                    crop = TextBounds(0, 0, bitmap.width, bitmap.height),
                    sensorToBufferMatrix = emptyList(),
                ),
            ),
            identity = FrameIdentity(4, 2),
        )))
        return owner
    }
}
