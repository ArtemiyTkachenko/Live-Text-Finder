package com.artkachenko.livetextfinder.camera

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.ocr.CapturedCameraFrame
import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import com.artkachenko.livetextfinder.ocr.TextPoint
import com.artkachenko.livetextfinder.search.FrameIdentity
import com.artkachenko.livetextfinder.search.HighlightColor
import com.artkachenko.livetextfinder.search.PreviewPoint
import com.artkachenko.livetextfinder.search.drawHighlightOutlines
import com.artkachenko.livetextfinder.search.importedHighlightsForDraw
import com.artkachenko.livetextfinder.search.manualSearchSpec
import com.artkachenko.livetextfinder.search.projectFrozenHighlights
import com.artkachenko.livetextfinder.search.searchRecognizedContent
import com.artkachenko.livetextfinder.search.searchRecognizedText
import com.artkachenko.livetextfinder.ui.StillInspectionViewport
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val VIEWPORT_WIDTH = 120
private const val VIEWPORT_HEIGHT = 80
private const val PARENT_WIDTH = 140
private const val PARENT_HEIGHT = 110
private const val HIGHLIGHT_COLOR = AndroidColor.CYAN

private object StillBitmapTestMotionDurationScale : MotionDurationScale {
    override val scaleFactor: Float = 0f
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StillBitmapInspectionTest {
    private var activity: ComponentActivity? = null
    private var composeView: ComposeView? = null
    private var frameClock: BroadcastFrameClock? = null
    private var recomposer: Recomposer? = null
    private var scope: CoroutineScope? = null
    private var nextFrameNanos = 1_000_000_000L

    @After
    fun disposeComposition() {
        clearComposition()
    }

    @Test
    fun productionFrozenBitmapAndOutlinesStayAlignedThroughZoomPanAndAllRotations() {
        listOf(0, 90, 180, 270).forEach { rotation ->
            val bitmap = asymmetricBitmap()
            val fixture = frozenFixture(bitmap, rotation)
            val fit = FrozenFrameFit(
                bitmap.width,
                bitmap.height,
                rotation,
                VIEWPORT_WIDTH.toFloat(),
                VIEWPORT_HEIGHT.toFloat(),
            )
            val transform = StillViewTransform().zoomBy(
                factor = 1.5f,
                centroid = PreviewPoint(VIEWPORT_WIDTH / 2f, VIEWPORT_HEIGHT / 2f),
                viewportWidth = VIEWPORT_WIDTH.toFloat(),
                viewportHeight = VIEWPORT_HEIGHT.toFloat(),
                fit = fit,
                panX = 8f,
                panY = 4f,
            )
            val search = searchRecognizedText(
                manualSearchSpec("target"),
                OcrUiState(OcrStatus.TextRecognized, snapshot = fixture.frame.snapshot),
            )
            val outlines = projectFrozenHighlights(
                search,
                VIEWPORT_WIDTH.toFloat(),
                VIEWPORT_HEIGHT.toFloat(),
            )
            assertEquals(1, outlines.outlines.size)
            val rendered = renderViewport(
                sourceIdentity = "frozen:${fixture.frame.requestId}",
                fit = fit,
                transform = transform,
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawFrozenBitmap(fixture.owner, fixture.frame, bitmap) { true }
                    drawHighlightOutlines(outlines.outlines, HighlightColor.fromRgb(0, 255, 255))
                }
            }

            assertBitmapLandmarks(rendered, bitmap, rotation, fit, transform)
            assertOutlineAligned(rendered, outlines.outlines.single().points, transform)
            assertEquals(AndroidColor.MAGENTA, rendered.getPixel(130, 85))
            saveVisual(rendered, "frozen-rotation-$rotation.png")
            rendered.recycle()

            val reset = StillViewTransform()
            val resetRender = renderViewport(
                sourceIdentity = "frozen:${fixture.frame.requestId}",
                fit = fit,
                transform = reset,
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawFrozenBitmap(fixture.owner, fixture.frame, bitmap) { true }
                    drawHighlightOutlines(
                        outlines.outlines,
                        HighlightColor.fromRgb(0, 255, 255),
                    )
                }
            }
            assertBitmapLandmarks(resetRender, bitmap, rotation, fit, reset)
            assertOutlineAligned(resetRender, outlines.outlines.single().points, reset)
            resetRender.recycle()

            val resizedWidth = 140
            val resizedHeight = 100
            val resizedFit = FrozenFrameFit(
                bitmap.width,
                bitmap.height,
                rotation,
                resizedWidth.toFloat(),
                resizedHeight.toFloat(),
            )
            val resizedOutlines = projectFrozenHighlights(
                search,
                resizedWidth.toFloat(),
                resizedHeight.toFloat(),
            )
            val resizedRender = renderViewport(
                sourceIdentity = "frozen:${fixture.frame.requestId}",
                fit = resizedFit,
                transform = reset,
                viewportWidth = resizedWidth,
                viewportHeight = resizedHeight,
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawFrozenBitmap(fixture.owner, fixture.frame, bitmap) { true }
                    drawHighlightOutlines(
                        resizedOutlines.outlines,
                        HighlightColor.fromRgb(0, 255, 255),
                    )
                }
            }
            assertBitmapLandmarks(
                resizedRender,
                bitmap,
                rotation,
                resizedFit,
                reset,
                resizedWidth,
                resizedHeight,
            )
            assertOutlineAligned(
                resizedRender,
                resizedOutlines.outlines.single().points,
                reset,
                resizedWidth,
                resizedHeight,
            )
            resizedRender.recycle()
            fixture.owner.stop()
            bitmap.recycle()
            clearComposition()
        }
    }

    @Test
    fun productionImportedBitmapAndOutlinesStayAlignedAndViewportClipsOverflow() {
        val bitmap = asymmetricBitmap()
        val fixture = importedFixture(bitmap)
        val fit = FrozenFrameFit(
            bitmap.width,
            bitmap.height,
            0,
            VIEWPORT_WIDTH.toFloat(),
            VIEWPORT_HEIGHT.toFloat(),
        )
        val transform = StillViewTransform().zoomBy(
            factor = 1.5f,
            centroid = PreviewPoint(VIEWPORT_WIDTH / 2f, VIEWPORT_HEIGHT / 2f),
            viewportWidth = VIEWPORT_WIDTH.toFloat(),
            viewportHeight = VIEWPORT_HEIGHT.toFloat(),
            fit = fit,
            panX = 8f,
            panY = 4f,
        )
        val search = searchRecognizedContent(manualSearchSpec("target"), fixture.snapshot)
        val outlines = importedHighlightsForDraw(
            search = search,
            displayedSnapshot = fixture.snapshot,
            currentImportState = fixture.owner.state.value,
            displayedSearchIdentity = search.spec.identity,
            currentSearchIdentity = search.spec.identity,
            viewportWidth = VIEWPORT_WIDTH.toFloat(),
            viewportHeight = VIEWPORT_HEIGHT.toFloat(),
            contentVisible = true,
        )
        assertTrue(outlines.ready)
        assertEquals(1, outlines.outlines.size)
        val rendered = renderViewport(
            sourceIdentity = "import:${fixture.snapshot.sourceIdentity}",
            fit = fit,
            transform = transform,
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawImportedBitmap(
                    fixture.owner,
                    fixture.snapshot.sourceIdentity,
                    bitmap,
                ) { true }
                val currentOutlines = importedHighlightsForDraw(
                    search,
                    fixture.owner.state.value.snapshot,
                    fixture.owner.state.value,
                    search.spec.identity,
                    search.spec.identity,
                    size.width,
                    size.height,
                    true,
                )
                drawHighlightOutlines(currentOutlines.outlines, HighlightColor.fromRgb(0, 255, 255))
            }
        }

        assertBitmapLandmarks(rendered, bitmap, 0, fit, transform)
        assertOutlineAligned(rendered, outlines.outlines.single().points, transform)
        assertEquals(AndroidColor.MAGENTA, rendered.getPixel(130, 85))
        saveVisual(rendered, "imported-zoom-pan.png")
        rendered.recycle()
        fixture.owner.dispose()
        bitmap.recycle()
    }

    private fun renderViewport(
        sourceIdentity: String,
        fit: FrozenFrameFit,
        transform: StillViewTransform,
        viewportWidth: Int = VIEWPORT_WIDTH,
        viewportHeight: Int = VIEWPORT_HEIGHT,
        content: @Composable BoxScope.() -> Unit,
    ): Bitmap {
        mount {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) { drawRect(Color.Magenta) }
                StillInspectionViewport(
                    sourceIdentity = sourceIdentity,
                    fit = fit,
                    viewportSize = IntSize(viewportWidth, viewportHeight),
                    transform = transform,
                    onTransformChange = {
                    },
                    modifier = Modifier.align(Alignment.TopStart)
                        .size(viewportWidth.dp, viewportHeight.dp),
                    content = content,
                )
            }
        }
        val view = checkNotNull(composeView)
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
            bitmap ->
            view.draw(AndroidCanvas(bitmap))
        }
    }

    private fun assertBitmapLandmarks(
        rendered: Bitmap,
        source: Bitmap,
        rotation: Int,
        fit: FrozenFrameFit,
        transform: StillViewTransform,
        viewportWidth: Int = VIEWPORT_WIDTH,
        viewportHeight: Int = VIEWPORT_HEIGHT,
    ) {
        val samples = listOf(
            Triple(20.5f, 10.5f, AndroidColor.RED),
            Triple(60.5f, 10.5f, AndroidColor.GREEN),
            Triple(20.5f, 35.5f, AndroidColor.BLUE),
            Triple(60.5f, 35.5f, AndroidColor.YELLOW),
        )
        samples.forEach { (rawX, rawY, expectedColor) ->
            val upright = fit.mapRawPoint(PreviewPoint(rawX, rawY))
            assertTrue(upright != null, "Invalid raw-to-upright mapping for rotation $rotation")
            val point = transform.map(upright, viewportWidth.toFloat(), viewportHeight.toFloat())
            assertTrue(point != null, "Invalid transformed landmark for rotation $rotation")
            assertTrue(
                nearColor(rendered, point, expectedColor),
                "Missing bitmap landmark $expectedColor at $point for rotation $rotation " +
                    "(source ${source.width}x${source.height})",
            )
        }
    }

    private fun assertOutlineAligned(
        rendered: Bitmap,
        points: List<PreviewPoint>,
        transform: StillViewTransform,
        viewportWidth: Int = VIEWPORT_WIDTH,
        viewportHeight: Int = VIEWPORT_HEIGHT,
    ) {
        val midpoint = PreviewPoint(
            (points[0].x + points[1].x) / 2f,
            (points[0].y + points[1].y) / 2f,
        )
        val transformed = transform.map(
            midpoint,
            viewportWidth.toFloat(),
            viewportHeight.toFloat(),
        )
        assertTrue(transformed != null)
        assertTrue(
            nearColor(rendered, transformed, HIGHLIGHT_COLOR),
            "Production highlight outline did not move with the bitmap at $transformed",
        )
    }

    private fun nearColor(bitmap: Bitmap, point: PreviewPoint, expected: Int): Boolean {
        val centerX = point.x.roundToInt()
        val centerY = point.y.roundToInt()
        for (y in (centerY - 1)..(centerY + 1)) {
            for (x in (centerX - 1)..(centerX + 1)) {
                if (x in 0 until bitmap.width && y in 0 until bitmap.height &&
                    bitmap.getPixel(x, y) == expected
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun targetBoundsForRotation(rotation: Int): TextBounds =
        if (rotation % 180 == 0) TextBounds(32, 20, 48, 28) else TextBounds(20, 32, 28, 48)

    private fun frozenFixture(bitmap: Bitmap, rotation: Int): FrozenFixture {
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
        val bounds = targetBoundsForRotation(rotation)
        val snapshot = OcrSnapshot(
            text = "target",
            lines = listOf(targetLine(bounds)),
            frame = FrameInfo(
                timestampNanos = 42L,
                bufferWidth = bitmap.width,
                bufferHeight = bitmap.height,
                rotationDegrees = rotation,
                crop = TextBounds(0, 0, bitmap.width, bitmap.height),
                sensorToBufferMatrix = emptyList(),
            ),
        )
        assertTrue(
            owner.accept(
                CapturedCameraFrame(
                    requestId = requestId,
                    bitmap = bitmap,
                    snapshot = snapshot,
                    identity = FrameIdentity(4, rotation.toLong() + 2L),
                ),
            ),
        )
        return FrozenFixture(owner, checkNotNull(owner.state.value.frame))
    }

    private fun importedFixture(bitmap: Bitmap): ImportedFixture {
        val owner = ImportedImageOwner()
        val requestId = checkNotNull(owner.beginPicker())
        val snapshot = ImportedTextSnapshot(
            text = "target",
            lines = listOf(targetLine(targetBoundsForRotation(0))),
            width = bitmap.width,
            height = bitmap.height,
            sourceIdentity = 84L,
        )
        assertTrue(owner.adoptProcessedImage(requestId, ProcessedImportedImage(bitmap, snapshot)))
        return ImportedFixture(owner, snapshot)
    }

    private fun targetLine(bounds: TextBounds) = TextLine(
        text = "diagnostic line",
        bounds = null,
        corners = emptyList(),
        elements = listOf(
            TextElement(
                text = "target",
                bounds = bounds,
                corners = listOf(
                    TextPoint(bounds.left, bounds.top),
                    TextPoint(bounds.right, bounds.top),
                    TextPoint(bounds.right, bounds.bottom),
                    TextPoint(bounds.left, bounds.bottom),
                ),
            ),
        ),
    )

    private fun asymmetricBitmap(): Bitmap = Bitmap.createBitmap(80, 48, Bitmap.Config.ARGB_8888)
        .apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setPixel(
                        x,
                        y,
                        when {
                            x < 40 && y < 24 -> AndroidColor.RED
                            x >= 40 && y < 24 -> AndroidColor.GREEN
                            x < 40 -> AndroidColor.BLUE
                            else -> AndroidColor.YELLOW
                        },
                    )
                }
            }
        }

    private fun saveVisual(bitmap: Bitmap, fileName: String) {
        val outputDirectory = System.getenv("LTF_VISUAL_OUTPUT_DIR") ?: return
        File(outputDirectory).mkdirs()
        FileOutputStream(File(outputDirectory, fileName)).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun mount(content: @Composable () -> Unit) {
        clearComposition()
        val hostActivity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val hostView = ComposeView(hostActivity)
        hostView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        val testFrameClock = BroadcastFrameClock()
        val recomposerScope = CoroutineScope(
            SupervisorJob() + Dispatchers.Unconfined + testFrameClock +
                StillBitmapTestMotionDurationScale,
        )
        val testRecomposer = Recomposer(recomposerScope.coroutineContext)
        recomposerScope.launch { testRecomposer.runRecomposeAndApplyChanges() }
        hostView.setParentCompositionContext(testRecomposer)
        activity = hostActivity
        composeView = hostView
        frameClock = testFrameClock
        recomposer = testRecomposer
        scope = recomposerScope
        hostView.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) { content() }
        }
        hostActivity.setContentView(hostView)
        hostView.layoutParams = android.widget.FrameLayout.LayoutParams(PARENT_WIDTH, PARENT_HEIGHT)
        hostView.measure(
            View.MeasureSpec.makeMeasureSpec(PARENT_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(PARENT_HEIGHT, View.MeasureSpec.EXACTLY),
        )
        hostView.layout(0, 0, PARENT_WIDTH, PARENT_HEIGHT)
        measureCurrentComposition()
    }

    private fun measureCurrentComposition() {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        val clock = checkNotNull(frameClock)
        val activeRecomposer = checkNotNull(recomposer)
        repeat(32) {
            clock.sendFrame(nextFrameNanos)
            nextFrameNanos += 16_000_000L
            shadowOf(Looper.getMainLooper()).idle()
            composeView?.let { view ->
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(PARENT_WIDTH, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(PARENT_HEIGHT, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, PARENT_WIDTH, PARENT_HEIGHT)
                (view.getChildAt(0) as ViewRootForTest).measureAndLayoutForTest()
            }
            if (activeRecomposer.currentState.value == Recomposer.State.Idle) return
        }
        error("Still inspection composition did not become idle after 32 frames")
    }

    private fun clearComposition() {
        composeView?.disposeComposition()
        recomposer?.cancel()
        scope?.cancel()
        activity?.finish()
        shadowOf(Looper.getMainLooper()).idle()
        activity = null
        composeView = null
        frameClock = null
        recomposer = null
        scope = null
    }

    private data class FrozenFixture(
        val owner: FrozenFrameOwner,
        val frame: FrozenSearchFrame,
    )

    private data class ImportedFixture(
        val owner: ImportedImageOwner,
        val snapshot: ImportedTextSnapshot,
    )
}
