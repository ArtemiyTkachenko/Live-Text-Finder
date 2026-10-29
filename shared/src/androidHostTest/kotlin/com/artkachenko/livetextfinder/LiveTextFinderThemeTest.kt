package com.artkachenko.livetextfinder

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.artkachenko.livetextfinder.ui.FinderPrimaryAction
import com.artkachenko.livetextfinder.ui.FinderSearchControls
import com.artkachenko.livetextfinder.ui.FinderSpacing
import com.artkachenko.livetextfinder.ui.LiveTextFinderTheme
import com.artkachenko.livetextfinder.ui.fallbackColorScheme
import com.artkachenko.livetextfinder.ui.platformColorScheme
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
class LiveTextFinderThemeTest {
    private var activity: ComponentActivity? = null
    private var composeView: ComposeView? = null

    @After
    fun disposeComposition() {
        clearMountedView()
    }

    private fun clearMountedView() {
        composeView?.disposeComposition()
        activity?.finish()
        shadowOf(Looper.getMainLooper()).idle()
        composeView = null
        activity = null
    }

    @Test
    @Config(sdk = [30])
    fun androidBeforeDynamicColorUsesRequestedFallback() {
        val light = mutableStateOf<ColorScheme?>(null)
        val dark = mutableStateOf<ColorScheme?>(null)
        mount(widthDp = 360, heightDp = 800) {
            light.value = platformColorScheme(darkTheme = false, dynamicColor = true)
            dark.value = platformColorScheme(darkTheme = true, dynamicColor = true)
        }

        assertEquals(fallbackColorScheme(false), light.value)
        assertEquals(fallbackColorScheme(true), dark.value)
    }

    @Test
    @Config(sdk = [35])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun androidDynamicBranchResolvesOnSupportedApi() {
        val light = mutableStateOf<ColorScheme?>(null)
        val dark = mutableStateOf<ColorScheme?>(null)
        mount(widthDp = 360, heightDp = 800) {
            light.value = platformColorScheme(darkTheme = false, dynamicColor = true)
            dark.value = platformColorScheme(darkTheme = true, dynamicColor = true)
        }

        val expectedLight = dynamicLightColorScheme(checkNotNull(activity))
        val expectedDark = dynamicDarkColorScheme(checkNotNull(activity))
        assertEquals(expectedLight.primary, light.value?.primary)
        assertEquals(expectedLight.surface, light.value?.surface)
        assertEquals(expectedLight.onSurface, light.value?.onSurface)
        assertEquals(expectedLight.surfaceContainer, light.value?.surfaceContainer)
        assertEquals(expectedDark.primary, dark.value?.primary)
        assertEquals(expectedDark.surface, dark.value?.surface)
        assertEquals(expectedDark.onSurface, dark.value?.onSurface)
        assertEquals(expectedDark.surfaceContainer, dark.value?.surfaceContainer)
    }

    @Test
    @Config(sdk = [35])
    fun fallbackSchemesKeepUsedRolesReadableAndActionTouchTargetLargeEnough() {
        val light = fallbackColorScheme(darkTheme = false)
        val dark = fallbackColorScheme(darkTheme = true)
        assertTrue(contrastRatio(light.onSurface, light.surface) >= 4.5)
        assertTrue(contrastRatio(light.primary, light.surfaceContainer) >= 4.5)
        assertTrue(contrastRatio(light.outline, light.surfaceContainer) >= 3.0)
        assertTrue(contrastRatio(light.onSurfaceVariant, light.surfaceContainer) >= 4.5)
        assertTrue(contrastRatio(light.onSecondaryContainer, light.secondaryContainer) >= 4.5)
        assertTrue(contrastRatio(dark.onSurface, dark.surface) >= 4.5)
        assertTrue(contrastRatio(dark.primary, dark.surfaceContainer) >= 4.5)
        assertTrue(contrastRatio(dark.outline, dark.surfaceContainer) >= 3.0)
        assertTrue(contrastRatio(dark.onSurfaceVariant, dark.surfaceContainer) >= 4.5)
        assertTrue(contrastRatio(dark.onSecondaryContainer, dark.secondaryContainer) >= 4.5)

        val actionSize = mutableStateOf(IntSize.Zero)
        mount(widthDp = 360, heightDp = 160) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                FinderPrimaryAction(
                    label = "Freeze frame",
                    enabled = true,
                    modifier = Modifier.onSizeChanged { actionSize.value = it },
                    onClick = ::ignoreAction,
                )
            }
        }
        val density = checkNotNull(composeView).resources.displayMetrics.density
        assertTrue(actionSize.value.height / density >= 48f)
    }

    @Test
    @Config(sdk = [35])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun productionControlsExposeQueryContextActionsAndNonOverlappingBounds() {
        val query = mutableStateOf("invoice-2048")
        val listButtonLabel = mutableStateOf("Search lists")
        val captureEnabled = mutableStateOf(false)
        val listClicks = mutableIntStateOf(0)
        val captureClicks = mutableIntStateOf(0)
        mount(widthDp = 360, heightDp = 800) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    FinderSearchControls(
                        showTitle = true,
                        query = query.value,
                        onQueryChange = { query.value = it },
                        searchListsLabel = listButtonLabel.value,
                        onSearchLists = {
                            listClicks.intValue++
                            listButtonLabel.value = "Close lists"
                        },
                        searchContext = "Monthly review · 4 terms",
                        onOptions = ::ignoreAction,
                        captureLabel = "Freeze frame",
                        captureEnabled = captureEnabled.value,
                        onCapture = { captureClicks.intValue++ },
                        pendingCaptureLabel = null,
                        onCancelCapture = ::ignoreAction,
                        secondaryActionLabel = null,
                        onSecondaryAction = ::ignoreAction,
                    )
                }
            }
        }

        var nodes = semanticsNodes()
        val queryNode = nodes.single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == "invoice-2048"
        }
        val setText = checkNotNull(queryNode.config.getOrNull(SemanticsActions.SetText)?.action)
        assertTrue(setText(AnnotatedString("updated invoice")))
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        assertEquals("updated invoice", query.value)
        nodes = semanticsNodes()
        assertTrue(
            nodes.any {
                it.config.getOrNull(SemanticsProperties.EditableText)?.text == "updated invoice"
            },
        )

        val listButton = nodeWithText(nodes, "Search lists", clickable = true)
        val listClick = checkNotNull(listButton.config.getOrNull(SemanticsActions.OnClick)?.action)
        assertTrue(listClick())
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        assertEquals(1, listClicks.intValue)
        nodes = semanticsNodes()
        assertTrue(nodeWithText(nodes, "Close lists", clickable = true).boundsInRoot.height > 0f)

        val disabledCapture = nodeWithText(nodes, "Freeze frame")
        assertTrue(disabledCapture.config.contains(SemanticsProperties.Disabled))
        captureEnabled.value = true
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        nodes = semanticsNodes()
        val enabledCapture = nodeWithText(nodes, "Freeze frame", clickable = true)
        assertTrue(!enabledCapture.config.contains(SemanticsProperties.Disabled))
        val captureClick = checkNotNull(
            enabledCapture.config.getOrNull(SemanticsActions.OnClick)?.action,
        )
        assertTrue(captureClick())
        assertEquals(1, captureClicks.intValue)

        val searchListsButton = nodeWithText(nodes, "Close lists", clickable = true)
        val contextText = nodeWithText(nodes, "Monthly review · 4 terms")
        val optionsButton = nodeWithText(nodes, "Options", clickable = true)
        val composeView = checkNotNull(this.composeView)
        val currentQueryNode = nodes.single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == "updated invoice"
        }
        val controls = listOf(
            searchListsButton,
            optionsButton,
            enabledCapture,
        )
        controls.forEach { assertTouchBounds(it) }
        val visibleBounds = listOf(
            currentQueryNode.unclippedBoundsInRoot(),
            searchListsButton.unclippedBoundsInRoot(),
            contextText.unclippedBoundsInRoot(),
            optionsButton.unclippedBoundsInRoot(),
            enabledCapture.unclippedBoundsInRoot(),
        )
        visibleBounds.forEach { bounds ->
            assertTrue(bounds.width > 0f && bounds.height > 0f)
            assertTrue(bounds.left >= 0f && bounds.right <= composeView.width)
            assertTrue(bounds.top >= 0f && bounds.bottom <= composeView.height)
        }
        for (firstIndex in controls.indices) {
            for (secondIndex in firstIndex + 1 until controls.size) {
                assertFalse(
                    intersects(
                        controls[firstIndex].touchBoundsInRoot,
                        controls[secondIndex].touchBoundsInRoot,
                    ),
                )
            }
        }
    }

    @Test
    @Config(sdk = [35])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun productionSearchControlFixturesKeepWeightedPreviewAndRenderWhenRequested() {
        val outputDirectory = System.getenv("LTF_VISUAL_OUTPUT_DIR")
        val fixtures = listOf(
            SearchControlFixture(
                fileName = "search-controls-light.png",
                dark = false,
                width = 360,
                height = 800,
                fontScale = 1f,
                hasPreview = true,
                scrollable = false,
                searchContext = "Monthly review · 4 terms",
            ),
            SearchControlFixture(
                fileName = "search-controls-dark.png",
                dark = true,
                width = 360,
                height = 800,
                fontScale = 1f,
                hasPreview = true,
                scrollable = false,
                searchContext = "Monthly review · 4 terms",
            ),
            SearchControlFixture(
                fileName = "search-controls-compact-large-font.png",
                dark = false,
                width = 360,
                height = 520,
                fontScale = 1.3f,
                hasPreview = false,
                scrollable = true,
                searchContext =
                    "Quarterly invoices and records retained for follow-up review · 4 terms",
            ),
            SearchControlFixture(
                fileName = "search-controls-landscape.png",
                dark = false,
                width = 600,
                height = 240,
                fontScale = 1f,
                hasPreview = false,
                scrollable = true,
                requiresOverflow = true,
                searchContext = "Monthly review · 4 terms",
            ),
        )

        fixtures.forEach { fixture ->
            clearMountedView()
            val controlsSize = mutableStateOf(IntSize.Zero)
            val placeholderPreviewSize = mutableStateOf(IntSize.Zero)
            val scrollState = ScrollState(0)
            mount(fixture.width, fixture.height) {
                LiveTextFinderTheme(
                    darkThemeOverride = fixture.dark,
                    dynamicColor = false,
                ) {
                    CompositionLocalProvider(
                        LocalDensity provides Density(density = 1f, fontScale = fixture.fontScale),
                    ) {
                        Surface(Modifier.fillMaxSize()) {
                            AppContentColumn(
                                scrollState = scrollState,
                                scrollable = fixture.scrollable,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Column(
                                    modifier = Modifier.onSizeChanged { controlsSize.value = it }
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = FinderSpacing.large,
                                            vertical = FinderSpacing.extraSmall,
                                        ),
                                ) {
                                    FinderSearchControls(
                                        showTitle = fixture.height >= 500 &&
                                            fixture.fontScale < 1.3f,
                                        query = "invoice-2048",
                                        onQueryChange = ::ignoreQueryChange,
                                        searchListsLabel = "Search lists",
                                        onSearchLists = ::ignoreAction,
                                        searchContext = fixture.searchContext,
                                        onOptions = ::ignoreAction,
                                        captureLabel = "Freeze frame",
                                        captureEnabled = true,
                                        onCapture = ::ignoreAction,
                                        pendingCaptureLabel = null,
                                        onCancelCapture = ::ignoreAction,
                                        secondaryActionLabel = null,
                                        onSecondaryAction = ::ignoreAction,
                                    )
                                }
                                if (fixture.hasPreview) {
                                    Box(
                                        modifier = Modifier.weight(1f)
                                            .fillMaxWidth()
                                            .background(Color.Black)
                                            .onSizeChanged {
                                                placeholderPreviewSize.value = it
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "Camera preview fixture · no live camera",
                                            color = Color.White,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val composeView = checkNotNull(composeView)
            measureCurrentComposition()
            if (fixture.scrollable) {
                runBlocking { scrollState.scrollTo(scrollState.maxValue) }
                measureCurrentComposition()
                if (fixture.requiresOverflow) {
                    assertTrue(scrollState.maxValue > 0)
                }
                assertEquals(scrollState.maxValue, scrollState.value)
                val options = nodeWithText(semanticsNodes(), "Options", clickable = true)
                val optionsBounds = options.unclippedBoundsInRoot()
                assertTrue(optionsBounds.height > 0f)
                assertTrue(optionsBounds.top >= 0f && optionsBounds.bottom <= composeView.height)
            }
            assertTrue(controlsSize.value.height > 0)
            val renderedNodes = semanticsNodes()
            val renderedQuery = renderedNodes.single {
                it.config.getOrNull(SemanticsProperties.EditableText) != null
            }
            val renderedContext = nodeWithText(renderedNodes, fixture.searchContext)
            val renderedControlBounds = listOf(
                renderedQuery.unclippedBoundsInRoot(),
                nodeWithText(renderedNodes, "Search lists", clickable = true)
                    .unclippedBoundsInRoot(),
                renderedContext.unclippedBoundsInRoot(),
                nodeWithText(renderedNodes, "Options", clickable = true)
                    .unclippedBoundsInRoot(),
                nodeWithText(renderedNodes, "Freeze frame", clickable = true)
                    .unclippedBoundsInRoot(),
            )
            renderedControlBounds.forEach { bounds ->
                assertTrue(bounds.width > 0f && bounds.height > 0f)
                assertTrue(bounds.left >= 0f && bounds.right <= composeView.width)
            }
            for (firstIndex in renderedControlBounds.indices) {
                for (secondIndex in firstIndex + 1 until renderedControlBounds.size) {
                    assertTrue(
                        !intersects(
                            renderedControlBounds[firstIndex],
                            renderedControlBounds[secondIndex],
                        ),
                    )
                }
            }
            if (fixture.searchContext.startsWith("Quarterly invoices")) {
                assertTrue(renderedContext.unclippedBoundsInRoot().height > 20f)
            }
            if (fixture.hasPreview) {
                assertEquals(
                    composeView.height - controlsSize.value.height,
                    placeholderPreviewSize.value.height,
                )
                assertTrue(placeholderPreviewSize.value.height > 0)
            } else {
                assertEquals(IntSize.Zero, placeholderPreviewSize.value)
            }
            val bitmap = captureRenderedBitmap()
            assertTrue(countDistinctColors(bitmap) > 16)
            if (outputDirectory != null) {
                saveRenderedFixture(outputDirectory, fixture.fileName, bitmap)
            }
            bitmap.recycle()
        }
    }

    private fun mount(
        widthDp: Int,
        heightDp: Int,
        content: @Composable () -> Unit,
    ) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView = ComposeView(activity)
        composeView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        this.activity = activity
        this.composeView = composeView
        composeView.setContent {
            content()
        }
        activity.setContentView(composeView)
        measureAt(composeView, activity, widthDp, heightDp)
        shadowOf(Looper.getMainLooper()).idle()
        measureAt(composeView, activity, widthDp, heightDp)
        val composeRoot = composeView.getChildAt(0) as ViewRootForTest
        composeRoot.measureAndLayoutForTest()
    }

    private fun measureAt(
        composeView: ComposeView,
        activity: Activity,
        widthDp: Int,
        heightDp: Int,
    ) {
        val density = activity.resources.displayMetrics.density
        val widthPx = (widthDp * density).roundToInt()
        val heightPx = (heightDp * density).roundToInt()
        composeView.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, widthPx, heightPx)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        fun luminance(color: Color): Double {
            fun linear(channel: Double): Double =
                if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

            val value = color.toArgb()
            val red = linear(((value shr 16) and 0xFF) / 255.0)
            val green = linear(((value shr 8) and 0xFF) / 255.0)
            val blue = linear((value and 0xFF) / 255.0)
            return (0.2126 * red) + (0.7152 * green) + (0.0722 * blue)
        }

        val first = luminance(foreground)
        val second = luminance(background)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private fun semanticsNodes(): List<SemanticsNode> {
        val root = (checkNotNull(composeView).getChildAt(0) as ViewRootForTest)
            .semanticsOwner.rootSemanticsNode
        val nodes = mutableListOf<SemanticsNode>()

        fun collect(node: SemanticsNode) {
            nodes += node
            node.children.forEach(::collect)
        }

        collect(root)
        return nodes
    }

    private fun nodeWithText(
        nodes: List<SemanticsNode>,
        text: String,
        clickable: Boolean = false,
    ): SemanticsNode = checkNotNull(
        nodes.firstOrNull { node ->
            val nodeText = node.config.getOrNull(SemanticsProperties.Text)
                .orEmpty()
                .any { it.text == text }
            nodeText && (!clickable || node.config.contains(SemanticsActions.OnClick))
        },
    ) {
        "No semantics node with text '$text'"
    }

    private fun measureCurrentComposition() {
        val composeView = checkNotNull(composeView)
        shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
        val composeRoot = composeView.getChildAt(0) as ViewRootForTest
        composeRoot.measureAndLayoutForTest()
    }

    private fun assertTouchBounds(node: SemanticsNode) {
        val density = checkNotNull(composeView).resources.displayMetrics.density
        assertTrue(node.touchBoundsInRoot.width / density >= 48f)
        assertTrue(node.touchBoundsInRoot.height / density >= 48f)
    }

    private fun intersects(first: Rect, second: Rect): Boolean =
        first.left < second.right && first.right > second.left &&
            first.top < second.bottom && first.bottom > second.top

    private fun SemanticsNode.unclippedBoundsInRoot(): Rect {
        val position = positionInRoot
        return Rect(
            left = position.x,
            top = position.y,
            right = position.x + size.width,
            bottom = position.y + size.height,
        )
    }

    private fun captureRenderedBitmap(): Bitmap {
        val composeView = checkNotNull(composeView)
        val bitmap = Bitmap.createBitmap(
            composeView.width,
            composeView.height,
            Bitmap.Config.ARGB_8888,
        )
        composeView.draw(Canvas(bitmap))
        return bitmap
    }

    private fun countDistinctColors(bitmap: Bitmap): Int {
        val colors = mutableSetOf<Int>()
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                colors += bitmap.getPixel(x, y)
                if (colors.size > 16) return colors.size
            }
        }
        return colors.size
    }

    private fun saveRenderedFixture(outputDirectory: String, fileName: String, bitmap: Bitmap) {
        val outputFile = java.io.File(outputDirectory, fileName)
        outputFile.parentFile?.mkdirs()
        java.io.FileOutputStream(outputFile).use { stream ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    private fun ignoreAction() = Unit

    private fun ignoreQueryChange(query: String) = Unit

    private data class SearchControlFixture(
        val fileName: String,
        val dark: Boolean,
        val width: Int,
        val height: Int,
        val fontScale: Float,
        val hasPreview: Boolean,
        val scrollable: Boolean,
        val searchContext: String,
        val requiresOverflow: Boolean = false,
    )
}
