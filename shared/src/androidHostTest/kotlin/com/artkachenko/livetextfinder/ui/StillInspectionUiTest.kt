package com.artkachenko.livetextfinder.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.artkachenko.livetextfinder.StillMatchCopyCoordinator
import com.artkachenko.livetextfinder.StillMatchCopyOutcome
import com.artkachenko.livetextfinder.camera.FrozenFrameFit
import com.artkachenko.livetextfinder.camera.FrozenFrameOwner
import com.artkachenko.livetextfinder.camera.ImportedImageOwner
import com.artkachenko.livetextfinder.camera.ProcessedImportedImage
import com.artkachenko.livetextfinder.camera.StillViewTransform
import com.artkachenko.livetextfinder.camera.plainTextClipEntry
import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import com.artkachenko.livetextfinder.ocr.TextPoint
import com.artkachenko.livetextfinder.search.ActiveSearchSpec
import com.artkachenko.livetextfinder.search.PreviewPoint
import com.artkachenko.livetextfinder.search.SearchTerm
import com.artkachenko.livetextfinder.search.inMemorySearchSessionController
import com.artkachenko.livetextfinder.search.searchRecognizedContent
import com.artkachenko.livetextfinder.search.stillMatchRows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

private object StillImmediateMotionDurationScale : MotionDurationScale {
    override val scaleFactor: Float = 0f
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StillInspectionUiTest {
    private var activity: ComponentActivity? = null
    private var composeView: ComposeView? = null
    private var frameClock: BroadcastFrameClock? = null
    private var recomposer: Recomposer? = null
    private var scope: CoroutineScope? = null
    private var frameTimeNanos = 1_000_000_000L
    private var fixtureWidth = 0
    private var fixtureHeight = 0

    @After
    fun disposeComposition() {
        composeView?.disposeComposition()
        ShadowDialog.getLatestDialog()?.dismiss()
        recomposer?.cancel()
        scope?.cancel()
        activity?.finish()
    }

    @Test
    fun sharedViewportTransformsImageAndOverlayTogetherAndClipsTheSubtree() {
        mount(100, 100) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                val transform = StillViewTransform(zoom = 2f)
                Surface(Modifier.fillMaxSize()) {
                    StillInspectionViewport(
                        sourceIdentity = "frozen:request-1",
                        fit = FrozenFrameFit(100, 100, 0, 100f, 100f),
                        viewportSize = IntSize(100, 100),
                        transform = transform,
                        onTransformChange = {
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Canvas(Modifier.matchParentSize()) {
                            drawRect(
                                color = Color.Red,
                                topLeft = Offset(20f, 20f),
                                size = Size(20f, 20f),
                            )
                        }
                        Canvas(Modifier.matchParentSize()) {
                            drawRect(
                                color = Color.Green,
                                topLeft = Offset(28f, 28f),
                                size = Size(4f, 4f),
                            )
                        }
                    }
                }
            }
        }

        val rendered = renderRoot()
        try {
            // The 2x layer is centered at (50, 50): source points map to (2*x-50, 2*y-50).
            assertEquals(android.graphics.Color.RED, rendered.getPixel(20, 20))
            assertEquals(android.graphics.Color.GREEN, rendered.getPixel(8, 8))
            assertFalse(
                rendered.getPixel(35, 35) in setOf(
                    android.graphics.Color.RED,
                    android.graphics.Color.GREEN,
                ),
            )
            assertFalse(
                rendered.getPixel(0, 99) in setOf(
                    android.graphics.Color.RED,
                    android.graphics.Color.GREEN,
                ),
            )
        } finally {
            rendered.recycle()
        }
    }

    @Test
    fun rememberedTransformResetsForSourceAndViewportButSurvivesSearchAndColorChanges() {
        val source = mutableStateOf("frozen:request-1")
        val viewport = mutableStateOf(IntSize(100, 100))
        val query = mutableStateOf("first")
        val color = mutableIntStateOf(0)
        mount(240, 160) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                val transform = rememberStillViewTransform(source.value, viewport.value)
                Column {
                    Button(onClick = { transform.value = StillViewTransform(zoom = 2f) }) {
                        Text("Set zoom")
                    }
                    Text("${transform.value.zoom}:${query.value}:${color.intValue}")
                }
            }
        }

        clickText("Set zoom")
        assertTrue(nodes(composeRoot()).any { node -> nodeText(node) == "2.0:first:0" })
        query.value = "second"
        color.intValue = 1
        measureCurrentComposition()
        assertTrue(nodes(composeRoot()).any { node -> nodeText(node) == "2.0:second:1" })

        source.value = "frozen:request-2"
        measureCurrentComposition()
        assertTrue(nodes(composeRoot()).any { node -> nodeText(node) == "1.0:second:1" })
        clickText("Set zoom")
        viewport.value = IntSize(120, 100)
        measureCurrentComposition()
        assertTrue(nodes(composeRoot()).any { node -> nodeText(node) == "1.0:second:1" })
    }

    @Test
    fun zoomResetAndMatchControlsRemainAccessibleAtLargeFontAndCompactWidth() {
        val transform = mutableStateOf(StillViewTransform())
        val viewportWidth = 220f
        val viewportHeight = 180f
        val fit = FrozenFrameFit(100, 100, 0, viewportWidth, viewportHeight)
        val center = PreviewPoint(viewportWidth / 2f, viewportHeight / 2f)
        mount(220, 180, fontScale = 1.5f) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                StillInspectionControls(
                    matchCount = 2,
                    zoom = transform.value.zoom,
                    canZoom = true,
                    onZoomIn = {
                        transform.value = transform.value.zoomBy(
                            1.25f,
                            center,
                            viewportWidth,
                            viewportHeight,
                            fit,
                        )
                    },
                    onZoomOut = {
                        transform.value = transform.value.zoomBy(
                            0.8f,
                            center,
                            viewportWidth,
                            viewportHeight,
                            fit,
                        )
                    },
                    onReset = { transform.value = transform.value.reset() },
                    onMatches = {
                    },
                )
            }
        }

        fun action(text: String): SemanticsNode = nodes(composeRoot()).first { node ->
            nodeText(node) == text && node.config.getOrNull(SemanticsActions.OnClick) != null
        }

        assertTrue(action("Zoom in").config.getOrNull(SemanticsProperties.Disabled) == null)
        assertNotNull(action("Zoom out").config.getOrNull(SemanticsProperties.Disabled))
        assertNotNull(action("Reset view").config.getOrNull(SemanticsProperties.Disabled))
        assertTrue(action("Matches (2)").config.getOrNull(SemanticsProperties.Disabled) == null)
        listOf("Zoom in", "Zoom out", "Reset view", "Matches (2)").forEach { label ->
            val bounds = action(label).boundsInRoot
            assertTrue(bounds.width >= 48f && bounds.height >= 48f)
            assertTrue(bounds.right <= viewportWidth && bounds.bottom <= viewportHeight)
        }

        val zoomIn = checkNotNull(
            action("Zoom in").config.getOrNull(SemanticsActions.OnClick)?.action,
        )
        assertTrue(zoomIn())
        measureCurrentComposition()
        assertTrue(action("Zoom out").config.getOrNull(SemanticsProperties.Disabled) == null)
        assertTrue(action("Reset view").config.getOrNull(SemanticsProperties.Disabled) == null)
        val reset = checkNotNull(
            action("Reset view").config.getOrNull(SemanticsActions.OnClick)?.action,
        )
        assertTrue(reset())
        measureCurrentComposition()
        assertNotNull(action("Zoom out").config.getOrNull(SemanticsProperties.Disabled))
    }

    @Test
    fun dialogSelectionEnablesCopyAndUsesScrollableOccurrenceRows() {
        val selected = mutableStateOf<Int?>(null)
        val session = inMemorySearchSessionController()
        val snapshot = importedSnapshot((0 until 30).map { "match $it context" })
        val spec = ActiveSearchSpec(
            identity = session.state.value.activeSearch.identity,
            displayName = "Parts",
            terms = (0 until 30).map { index -> SearchTerm("term-$index", "match $index") },
        )
        val result = searchRecognizedContent(spec, snapshot)
        val importedOwner = ImportedImageOwner()
        val requestId = checkNotNull(importedOwner.beginPicker())
        check(
            importedOwner.adoptProcessedImage(
                requestId,
                ProcessedImportedImage(
                    Bitmap.createBitmap(100, 20, Bitmap.Config.ARGB_8888),
                    snapshot,
                ),
            ),
        )
        val frozenOwner = FrozenFrameOwner()
        val rows = stillMatchRows(result)
        val copied = mutableListOf<String>()
        val clipboard = object : Clipboard {
            override suspend fun getClipEntry(): ClipEntry? = null

            override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                copied.add(checkNotNull(clipEntry).clipData.getItemAt(0).text.toString())
            }
        }
        val coordinator = StillMatchCopyCoordinator(
            importedOwner = importedOwner,
            frozenOwner = frozenOwner,
            session = session,
            selectedIndex = { selected.value },
            writeText = { clipboard.setClipEntry(plainTextClipEntry(it)) },
        )
        val copyMessage = mutableStateOf<String?>(null)
        mount(360, 520) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                StillMatchesDialog(
                    rows = rows,
                    selectedIndex = selected.value,
                    copyMessage = copyMessage.value,
                    onSelect = { selected.value = it },
                    onCopy = {
                        val index = selected.value ?: return@StillMatchesDialog
                        val ownerToken = importedOwner.state.value.snapshot
                            ?: return@StillMatchesDialog
                        val outcome = runBlocking {
                            coordinator.copy(
                                rows[index],
                                result,
                                "import:${snapshot.sourceIdentity}",
                                result.spec.identity,
                                index,
                                ownerToken,
                            )
                        }
                        if (outcome == StillMatchCopyOutcome.Copied) {
                            copyMessage.value = "Copied"
                        } else {
                            copyMessage.value = "Could not copy this match. Select it again."
                        }
                    },
                    onDismiss = {
                    },
                )
            }
        }

        val dialogRoot = dialogComposeRoot()
        val scrollable = nodes(dialogRoot).single { node ->
            node.config.getOrNull(SemanticsActions.ScrollBy) != null
        }
        assertNotNull(scrollable)
        val disabledCopy = nodes(dialogRoot).first { node ->
            nodeText(node).contains("Copy matched text")
        }
        assertNotNull(disabledCopy.config.getOrNull(SemanticsProperties.Disabled))
        val rowNode = nodes(dialogRoot).first { node ->
            nodeText(node).contains("match 2") &&
                node.config.getOrNull(SemanticsActions.OnClick) != null
        }
        assertTrue(checkNotNull(rowNode.config.getOrNull(SemanticsActions.OnClick)?.action)())
        measureCurrentComposition()
        assertTrue(
            nodes(dialogComposeRoot()).any { node ->
                node.config.getOrNull(SemanticsProperties.Selected) == true
            },
        )
        val copy = nodes(dialogComposeRoot()).first { node ->
            nodeText(node).contains("Copy matched text") &&
                node.config.getOrNull(SemanticsActions.OnClick) != null
        }
        assertTrue(checkNotNull(copy.config.getOrNull(SemanticsActions.OnClick)?.action)())
        measureCurrentComposition()
        assertEquals(listOf("match 2"), copied)

        val scrollAction = checkNotNull(
            scrollable.config.getOrNull(SemanticsActions.ScrollBy)?.action,
        )
        assertTrue(scrollAction(0f, 8_000f))
        measureCurrentComposition()
        var dialogNodes = nodes(dialogComposeRoot())
        assertTrue(dialogNodes.any { node -> nodeText(node).contains("match 29") })
        val lastRow = dialogNodes.first { node ->
            node.config.getOrNull(SemanticsProperties.Text)
                .orEmpty().any { it.text == "match 29" } &&
                node.config.getOrNull(SemanticsActions.OnClick) != null
        }
        assertTrue(checkNotNull(lastRow.config.getOrNull(SemanticsActions.OnClick)?.action)())
        measureCurrentComposition()
        dialogNodes = nodes(dialogComposeRoot())
        val copyScrolled = dialogNodes.first { node ->
            nodeText(node).contains("Copy matched text") &&
                node.config.getOrNull(SemanticsActions.OnClick) != null
        }
        assertTrue(checkNotNull(copyScrolled.config.getOrNull(SemanticsActions.OnClick)?.action)())
        measureCurrentComposition()
        assertEquals(listOf("match 2", "match 29"), copied)
        importedOwner.dispose()
        frozenOwner.stop()
    }

    private fun mount(
        widthDp: Int,
        heightDp: Int,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        val hostActivity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val hostView = ComposeView(hostActivity)
        hostView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        val testFrameClock = BroadcastFrameClock()
        val recomposerScope = CoroutineScope(
            SupervisorJob() + Dispatchers.Unconfined + testFrameClock +
                StillImmediateMotionDurationScale,
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
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale),
            ) {
                content()
            }
        }
        hostActivity.setContentView(hostView)
        val density = 1f
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        fixtureWidth = width
        fixtureHeight = height
        hostView.layoutParams = android.widget.FrameLayout.LayoutParams(width, height)
        hostView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        hostView.layout(0, 0, width, height)
        measureCurrentComposition()
    }

    private fun renderRoot(): Bitmap {
        val view = checkNotNull(composeView)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(AndroidCanvas(bitmap))
        return bitmap
    }

    private fun dialogComposeRoot(): ViewRootForTest = findViewRoot(
        checkNotNull(ShadowDialog.getLatestDialog()?.window?.decorView),
    )

    private fun composeRoot(): ViewRootForTest = findViewRoot(checkNotNull(composeView))

    private fun clickText(text: String) {
        val node = nodes(composeRoot()).first { node ->
            nodeText(node) == text && node.config.getOrNull(SemanticsActions.OnClick) != null
        }
        assertTrue(checkNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.action)())
        measureCurrentComposition()
    }

    private fun findViewRoot(view: View): ViewRootForTest = when (view) {
        is ViewRootForTest -> view
        is android.view.ViewGroup -> (0 until view.childCount)
            .asSequence()
            .mapNotNull { index ->
                runCatching { findViewRoot(view.getChildAt(index)) }.getOrNull()
            }
            .firstOrNull()
            ?: error("No Compose view root found")
        else -> error("No Compose view root found")
    }

    private fun nodes(root: ViewRootForTest): List<SemanticsNode> = buildList {
        fun collect(node: SemanticsNode) {
            add(node)
            node.children.forEach(::collect)
        }
        collect(root.semanticsOwner.rootSemanticsNode)
    }

    private fun nodeText(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }

    private fun snapshot(lines: List<String>) = OcrSnapshot(
        text = "aggregate diagnostic text",
        lines = lines.map { text ->
            TextLine(
                "diagnostic line",
                null,
                emptyList(),
                listOf(TextElement(text, TextBounds(0, 0, 100, 20), listOf(TextPoint(0, 0)))),
            )
        },
        frame = FrameInfo(1, 100, 100, 0, TextBounds(0, 0, 100, 100), emptyList()),
    )

    private fun importedSnapshot(lines: List<String>) = ImportedTextSnapshot(
        text = lines.joinToString(" "),
        lines = lines.map { text ->
            TextLine(
                text,
                null,
                emptyList(),
                listOf(TextElement(text, TextBounds(0, 0, 100, 20), listOf(TextPoint(0, 0)))),
            )
        },
        width = 100,
        height = 20,
        sourceIdentity = 44,
    )

    private fun measureCurrentComposition() {
        Snapshot.sendApplyNotifications()
        val testFrameClock = checkNotNull(frameClock)
        val testRecomposer = checkNotNull(recomposer)
        repeat(32) {
            testFrameClock.sendFrame(frameTimeNanos)
            frameTimeNanos += 16_000_000L
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeView?.let { view ->
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(fixtureWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(fixtureHeight, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, fixtureWidth, fixtureHeight)
                findViewRoot(view).measureAndLayoutForTest()
            }
            ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView?.let {
                runCatching { findViewRoot(it).measureAndLayoutForTest() }
            }
            if (testRecomposer.currentState.value == Recomposer.State.Idle) return
        }
        error("Compose recomposer did not become idle after 32 frames")
    }
}
