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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.semantics.SemanticsProperties.Focused
import androidx.compose.ui.semantics.SemanticsProperties.TestTag
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.artkachenko.livetextfinder.search.SearchMatchMode
import com.artkachenko.livetextfinder.search.inMemorySearchSessionController
import com.artkachenko.livetextfinder.ui.FinderCaptureControls
import com.artkachenko.livetextfinder.ui.FinderContentAssembly
import com.artkachenko.livetextfinder.ui.FinderLiveAppBar
import com.artkachenko.livetextfinder.ui.FinderLiveDock
import com.artkachenko.livetextfinder.ui.FinderPrimaryAction
import com.artkachenko.livetextfinder.ui.FinderSearchControls
import com.artkachenko.livetextfinder.ui.FinderSpacing
import com.artkachenko.livetextfinder.ui.LiveTextFinderTheme
import com.artkachenko.livetextfinder.ui.StatusSummary
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
    fun defaultThemeUsesBrandedPaletteAndKeepsLightDarkOverride() {
        val light = mutableStateOf<ColorScheme?>(null)
        val dark = mutableStateOf<ColorScheme?>(null)
        mount(widthDp = 360, heightDp = 800) {
            LiveTextFinderTheme(darkThemeOverride = false) {
                light.value = MaterialTheme.colorScheme
            }
            LiveTextFinderTheme(darkThemeOverride = true) {
                dark.value = MaterialTheme.colorScheme
            }
        }

        assertEquals(Color(0xFF006B58), light.value?.primary)
        assertEquals(Color(0xFF0C1016), dark.value?.background)
        assertEquals(Color(0xFF171D25), dark.value?.surfaceContainer)
        assertEquals(Color(0xFF232B36), dark.value?.surfaceContainerHigh)
        assertEquals(Color(0xFF334155), dark.value?.outlineVariant)
        assertEquals(Color(0xFFF4F7FA), dark.value?.onSurface)
        assertEquals(Color(0xFFA5B2C0), dark.value?.onSurfaceVariant)
        assertEquals(Color(0xFF60E1C1), dark.value?.primary)
        assertEquals(Color(0xFF09221C), dark.value?.onPrimary)
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
        val clicks = mutableIntStateOf(0)
        mount(widthDp = 360, heightDp = 160) {
            LiveTextFinderTheme(darkThemeOverride = false) {
                FinderPrimaryAction(
                    label = "Freeze frame",
                    enabled = true,
                    modifier = Modifier.onSizeChanged { actionSize.value = it },
                    onClick = { clicks.intValue++ },
                )
            }
        }
        val density = checkNotNull(composeView).resources.displayMetrics.density
        assertTrue(actionSize.value.height / density >= 52f)
        assertTrue(actionSize.value.width / density >= 48f)
        val action = nodeWithText(semanticsNodes(), "Freeze frame", clickable = true)
        assertTrue(checkNotNull(action.config.getOrNull(SemanticsActions.OnClick)?.action)())
        assertEquals(1, clicks.intValue)
    }

    @Test
    @Config(sdk = [35])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun productionControlsExposeQueryContextActionsAndNonOverlappingBounds() {
        val query = mutableStateOf("invoice-2048")
        val listButtonLabel = mutableStateOf("Lists")
        val captureEnabled = mutableStateOf(false)
        val listClicks = mutableIntStateOf(0)
        val captureClicks = mutableIntStateOf(0)
        val resultsClicks = mutableIntStateOf(0)
        val previewSize = mutableStateOf(IntSize.Zero)
        mount(widthDp = 360, heightDp = 800) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    FinderContentAssembly(
                        scrollState = ScrollState(0),
                        scrollable = false,
                        modifier = Modifier.fillMaxSize(),
                        topContent = {
                            FinderSearchControls(
                                showTitle = true,
                                query = query.value,
                                onQueryChange = { query.value = it },
                                searchListsLabel = listButtonLabel.value,
                                onSearchLists = {
                                    listClicks.intValue++
                                    listButtonLabel.value = "Close lists"
                                },
                                onOptions = ::ignoreAction,
                                resultsLabel = "Results (3)",
                                onResults = { resultsClicks.intValue++ },
                            )
                        },
                        managerContent = {},
                        viewportContent = {
                            Box(
                                Modifier.weight(1f).fillMaxWidth().onSizeChanged {
                                    previewSize.value = it
                                },
                            )
                        },
                        footerContent = {
                            FinderCaptureControls(
                                captureLabel = "Freeze frame",
                                captureEnabled = captureEnabled.value,
                                onCapture = { captureClicks.intValue++ },
                                pendingCaptureLabel = null,
                                onCancelCapture = ::ignoreAction,
                                secondaryActionLabel = null,
                                onSecondaryAction = ::ignoreAction,
                            )
                        },
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

        val listButton = nodeWithText(nodes, "Lists", clickable = true)
        val listClick = checkNotNull(listButton.config.getOrNull(SemanticsActions.OnClick)?.action)
        assertTrue(listClick())
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        assertEquals(1, listClicks.intValue)
        nodes = semanticsNodes()
        assertTrue(nodeWithText(nodes, "Close lists", clickable = true).boundsInRoot.height > 0f)
        val resultsAction = nodeWithText(nodes, "Results (3)", clickable = true)
        assertTrue(
            checkNotNull(resultsAction.config.getOrNull(SemanticsActions.OnClick)?.action)(),
        )
        assertEquals(1, resultsClicks.intValue)

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
        val contextText = nodeWithText(nodes, "Manual search")
        val optionsButton = nodeWithText(nodes, "Options", clickable = true)
        val composeView = checkNotNull(this.composeView)
        val currentQueryNode = nodes.single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == "updated invoice"
        }
        val controls = listOf(searchListsButton, optionsButton, enabledCapture)
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
        assertTrue(previewSize.value.height >= composeView.height / 2)
    }

    @Test
    @Config(sdk = [35])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun mainScannerHeaderAndFooterFixturesRenderAcrossSmallAndShortLayouts() {
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
                fileName = "search-controls-320-large-font-ime-like.png",
                dark = false,
                width = 320,
                height = 400,
                fontScale = 1.4f,
                hasPreview = false,
                scrollable = true,
                requiresOverflow = true,
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
            val footerSize = mutableStateOf(IntSize.Zero)
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
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            FinderContentAssembly(
                                scrollState = scrollState,
                                scrollable = fixture.scrollable,
                                modifier = Modifier.fillMaxSize(),
                                topContent = {
                                    Column(
                                        modifier = Modifier.onSizeChanged {
                                            controlsSize.value = it
                                        }.fillMaxWidth().padding(
                                            horizontal = FinderSpacing.large,
                                            vertical = FinderSpacing.extraSmall,
                                        ),
                                    ) {
                                        FinderSearchControls(
                                            showTitle = fixture.height >= 500 &&
                                                fixture.fontScale < 1.3f,
                                            query = "invoice-2048",
                                            onQueryChange = ::ignoreQueryChange,
                                            searchListsLabel = "Lists",
                                            onSearchLists = ::ignoreAction,
                                            searchContext = fixture.searchContext,
                                            onOptions = ::ignoreAction,
                                            resultsLabel = "Results (4)",
                                            onResults = ::ignoreAction,
                                        )
                                    }
                                },
                                managerContent = {},
                                viewportContent = {
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
                                },
                                footerContent = {
                                    Box(Modifier.onSizeChanged { footerSize.value = it }) {
                                        FinderCaptureControls(
                                            captureLabel = "Freeze frame",
                                            captureEnabled = true,
                                            onCapture = ::ignoreAction,
                                            pendingCaptureLabel = null,
                                            onCancelCapture = ::ignoreAction,
                                            secondaryActionLabel = null,
                                            onSecondaryAction = ::ignoreAction,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
            val composeView = checkNotNull(composeView)
            measureCurrentComposition()
            if (fixture.scrollable) {
                val topOptions = nodeWithText(semanticsNodes(), "Options", clickable = true)
                val topOptionsBounds = topOptions.unclippedBoundsInRoot()
                assertTrue(topOptionsBounds.top >= 0f)
                assertTrue(topOptionsBounds.bottom <= composeView.height)
                runBlocking { scrollState.scrollTo(scrollState.maxValue) }
                measureCurrentComposition()
                if (fixture.requiresOverflow) {
                    assertTrue(scrollState.maxValue > 0)
                }
                assertEquals(scrollState.maxValue, scrollState.value)
                val scrolledNodes = semanticsNodes()
                val capture = nodeWithText(scrolledNodes, "Freeze frame", clickable = true)
                assertTouchBounds(capture)
                assertTrue(capture.unclippedBoundsInRoot().bottom <= composeView.height)
            }
            assertTrue(controlsSize.value.height > 0)
            val renderedNodes = semanticsNodes()
            val renderedQuery = renderedNodes.single {
                it.config.getOrNull(SemanticsProperties.EditableText) != null
            }
            val renderedContext = nodeWithText(renderedNodes, fixture.searchContext)
            val renderedControlBounds = listOf(
                renderedQuery.unclippedBoundsInRoot(),
                nodeWithText(renderedNodes, "Lists", clickable = true)
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
                if (!fixture.scrollable) {
                    assertTrue(bounds.top >= 0f && bounds.bottom <= composeView.height)
                }
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
                    composeView.height - controlsSize.value.height - footerSize.value.height,
                    placeholderPreviewSize.value.height,
                )
                assertTrue(placeholderPreviewSize.value.height > 0)
            } else {
                assertEquals(IntSize.Zero, placeholderPreviewSize.value)
            }
            val bitmap = captureRenderedBitmap()
            assertTrue(countDistinctColors(bitmap) > 16)
            val expectedBackground = if (fixture.dark) Color(0xFF0C1016) else Color(0xFFF5F8F7)
            assertEquals(expectedBackground.toArgb(), bitmap.getPixel(0, 0))
            val expectedPrimary = if (fixture.dark) Color(0xFF60E1C1) else Color(0xFF006B58)
            val primaryPixels = countPixels(bitmap, expectedPrimary.toArgb())
            assertTrue(primaryPixels > 100, "$fixture primaryPixels=$primaryPixels")
            if (outputDirectory != null) {
                saveRenderedFixture(outputDirectory, fixture.fileName, bitmap)
            }
            bitmap.recycle()
        }
    }

    @Test
    @Config(sdk = [35])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun productionLiveManualDockPreservesViewportAndRoutesActions() {
        val viewportSize = mutableStateOf(IntSize.Zero)
        val statusSize = mutableStateOf(IntSize.Zero)
        val statusMessage = mutableStateOf("Scanning · Enter a query")
        val query = mutableStateOf("invoice-2048")
        val resultsLabel = mutableStateOf<String?>("Results (3)")
        val saving = mutableStateOf(false)
        val captureLabel = mutableStateOf<String?>("Freeze frame")
        val pendingCaptureLabel = mutableStateOf<String?>(null)
        val dockSize = mutableStateOf(IntSize.Zero)
        val safeSize = mutableStateOf(IntSize.Zero)
        val previewVisible = mutableStateOf(true)
        val darkTheme = mutableStateOf(false)
        val optionsClicks = mutableIntStateOf(0)
        val termsClicks = mutableIntStateOf(0)
        val listClicks = mutableIntStateOf(0)
        val resultsClicks = mutableIntStateOf(0)
        val captureClicks = mutableIntStateOf(0)
        val detailsClicks = mutableIntStateOf(0)
        mount(widthDp = 360, heightDp = 800) {
            LiveTextFinderTheme(darkThemeOverride = darkTheme.value, dynamicColor = false) {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    FinderContentAssembly(
                        scrollState = ScrollState(0),
                        scrollable = !previewVisible.value,
                        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()
                            .onSizeChanged { safeSize.value = it },
                        topContent = {
                            FinderLiveAppBar(onOptions = { optionsClicks.intValue++ })
                            if (previewVisible.value) {
                                StatusSummary(
                                    message = statusMessage.value,
                                    details = RecognitionDetails(
                                        source = StatusSource.LiveCamera,
                                        recognizedLineCount = 0,
                                        outlinedElementCount = 0,
                                        imageWidth = 1280,
                                        imageHeight = 720,
                                        rotationDegrees = 0,
                                        textSample = null,
                                        textUnavailable = true,
                                        retainedOutlines = false,
                                        sourceMessage = null,
                                    ),
                                    onDetails = { detailsClicks.intValue++ },
                                    modifier = Modifier.onSizeChanged { statusSize.value = it },
                                    stageBand = true,
                                )
                            }
                        },
                        managerContent = {},
                        viewportContent = {
                            if (previewVisible.value) {
                                Box(
                                    Modifier.weight(1f).fillMaxWidth().background(Color.Black)
                                        .onSizeChanged { viewportSize.value = it },
                                )
                            }
                        },
                        footerContent = {
                            Box(
                                Modifier.fillMaxWidth().onSizeChanged { dockSize.value = it },
                            ) {
                                FinderLiveDock(
                                    query = query.value,
                                    onQueryChange = { query.value = it },
                                    activeListName = null,
                                    activeListTermCount = 0,
                                    matchMode = SearchMatchMode.Substring,
                                    searchListsLabel = "Lists",
                                    onSearchLists = { listClicks.intValue++ },
                                    onTerms = { termsClicks.intValue++ },
                                    onUseManual = {},
                                    onResults = { resultsClicks.intValue++ },
                                    resultsLabel = resultsLabel.value,
                                    saving = saving.value,
                                    captureLabel = captureLabel.value,
                                    captureEnabled = true,
                                    onCapture = { captureClicks.intValue++ },
                                    pendingCaptureLabel = pendingCaptureLabel.value,
                                    onCancelCapture = ::ignoreAction,
                                    secondaryActionLabel = null,
                                    onSecondaryAction = ::ignoreAction,
                                )
                            }
                        },
                    )
                }
            }
        }

        val density = checkNotNull(composeView).resources.displayMetrics.density
        assertEquals(744f, safeSize.value.height / density)
        assertTrue(viewportSize.value.height / density >= 400f)
        val reservedStatusSize = statusSize.value
        val reservedViewportSize = viewportSize.value
        listOf(
            "Scanning · No readable text",
            "Scanning · No matches",
            "Scanning · 3 matches",
            "Scanning · Results unavailable",
            "Scanning · Waiting for new text…",
            "Scanning · Waiting for current search results · Some older highlights remain",
            "Scanning · No matches in search area · Some older highlights remain",
            "Scanning · No readable text · Some older highlights remain",
            "Scanning · 333 matches · Waiting to show highlights",
        ).forEach { message ->
            statusMessage.value = message
            Snapshot.sendApplyNotifications()
            measureCurrentComposition()
            assertEquals(reservedStatusSize, statusSize.value, message)
            assertEquals(reservedViewportSize, viewportSize.value, message)
            assertTrue(viewportSize.value.height / density >= 400f, message)
        }
        var nodes = semanticsNodes()
        val queryNode = nodes.single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == "invoice-2048"
        }
        assertTrue(queryNode.unclippedBoundsInRoot().height / density >= 56f)
        val setText = checkNotNull(queryNode.config.getOrNull(SemanticsActions.SetText)?.action)
        assertTrue(setText(AnnotatedString("updated invoice")))
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        assertEquals("updated invoice", query.value)
        nodes = semanticsNodes()

        val actions = listOf(
            nodeWithText(nodes, "Options", clickable = true),
            nodeWithText(nodes, "Details", clickable = true),
            nodeWithText(nodes, "Terms", clickable = true),
            nodeWithText(nodes, "Lists", clickable = true),
            nodeWithText(nodes, "Results (3)", clickable = true),
            nodeWithText(nodes, "Freeze frame", clickable = true),
        )
        val freezeButtonSize = nodeWithText(nodes, "Freeze frame", clickable = true)
            .touchBoundsInRoot.height
        actions.forEach(::assertTouchBounds)
        assertTrue(freezeButtonSize / density >= 52f)
        assertTrue(
            nodeWithText(nodes, "Freeze frame", clickable = true).touchBoundsInRoot.width /
                density >= 320f,
        )
        for (firstIndex in actions.indices) {
            for (secondIndex in firstIndex + 1 until actions.size) {
                assertFalse(
                    intersects(
                        actions[firstIndex].touchBoundsInRoot,
                        actions[secondIndex].touchBoundsInRoot,
                    ),
                )
            }
        }
        val clickActions = listOf(
            optionsClicks to "Options",
            detailsClicks to "Details",
            termsClicks to "Terms",
            listClicks to "Lists",
            resultsClicks to "Results (3)",
            captureClicks to "Freeze frame",
        )
        clickActions.forEach { (clickCount, label) ->
            val action = nodeWithText(semanticsNodes(), label, clickable = true)
            assertTrue(checkNotNull(action.config.getOrNull(SemanticsActions.OnClick)?.action)())
            assertEquals(1, clickCount.intValue, label)
        }
        query.value = ""
        resultsLabel.value = null
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        assertFalse(semanticsNodes().any { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any {
                it.text == "Results (3)"
            }
        })
        assertEquals(reservedViewportSize, viewportSize.value)
        query.value = "invoice-2048"
        resultsLabel.value = "Results (3)"
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        captureLabel.value = null
        pendingCaptureLabel.value = "Cancel freeze"
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        nodes = semanticsNodes()
        val cancelButton = nodeWithText(nodes, "Cancel freeze", clickable = true)
        assertTrue(cancelButton.touchBoundsInRoot.height >= freezeButtonSize)
        assertEquals(reservedStatusSize, statusSize.value)
        assertEquals(reservedViewportSize, viewportSize.value)
        assertTrue(viewportSize.value.height / density >= 400f)
        val reservedDockSize = dockSize.value
        saving.value = true
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        assertEquals(reservedDockSize, dockSize.value)
        assertEquals(reservedViewportSize, viewportSize.value)
        val focusableQuery = semanticsNodes().single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == "invoice-2048"
        }
        val requestFocus = checkNotNull(
            focusableQuery.config.getOrNull(SemanticsActions.RequestFocus)?.action,
        )
        assertTrue(requestFocus())
        previewVisible.value = false
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        nodes = semanticsNodes()
        val queryAfterPreviewHide = nodes.single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == "invoice-2048"
        }
        assertEquals(true, queryAfterPreviewHide.config.getOrNull(Focused))
        assertTrue(nodeWithText(nodes, "Options", clickable = true).boundsInRoot.height > 0f)
        assertTrue(
            nodeWithText(nodes, "Cancel freeze", clickable = true).boundsInRoot.height > 0f,
        )
        val setTextAfterPreviewHide = checkNotNull(
            queryAfterPreviewHide.config.getOrNull(SemanticsActions.SetText)?.action,
        )
        assertTrue(setTextAfterPreviewHide(AnnotatedString("kept through IME")))
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        assertEquals("kept through IME", query.value)
        query.value = "invoice-2048"
        resultsLabel.value = "Results (3)"
        saving.value = false
        captureLabel.value = "Freeze frame"
        pendingCaptureLabel.value = null
        statusMessage.value = "Scanning · 3 matches"
        previewVisible.value = true
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()

        val outputDirectory = System.getenv("LTF_VISUAL_OUTPUT_DIR")
        if (outputDirectory != null) {
            val bitmap = captureRenderedBitmap()
            saveRenderedFixture(outputDirectory, "live-dock-manual-light.png", bitmap)
            bitmap.recycle()
            darkTheme.value = true
            Snapshot.sendApplyNotifications()
            measureCurrentComposition()
            assertEquals(reservedViewportSize, viewportSize.value)
            val darkBitmap = captureRenderedBitmap()
            saveRenderedFixture(outputDirectory, "live-dock-manual-dark.png", darkBitmap)
            darkBitmap.recycle()
        }
    }

    @Test
    @Config(sdk = [35])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun productionLiveListDockShowsListIdentityAndRestoresManualSearch() {
        val viewportSize = mutableStateOf(IntSize.Zero)
        val darkTheme = mutableStateOf(true)
        val safeSize = mutableStateOf(IntSize.Zero)
        val useManualClicks = mutableIntStateOf(0)
        val query = mutableStateOf("retained private query")
        mount(widthDp = 360, heightDp = 800) {
            LiveTextFinderTheme(darkThemeOverride = darkTheme.value, dynamicColor = false) {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    FinderContentAssembly(
                        scrollState = ScrollState(0),
                        scrollable = false,
                        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()
                            .onSizeChanged { safeSize.value = it },
                        topContent = {
                            FinderLiveAppBar(onOptions = ::ignoreAction)
                            StatusSummary(
                                message = "Scanning · Results unavailable",
                                details = RecognitionDetails(
                                    source = StatusSource.LiveCamera,
                                    recognizedLineCount = 2,
                                    outlinedElementCount = 1,
                                    imageWidth = 1280,
                                    imageHeight = 720,
                                    rotationDegrees = 0,
                                    textSample = "Quarterly review",
                                    textUnavailable = false,
                                    retainedOutlines = false,
                                    sourceMessage = null,
                                ),
                                onDetails = ::ignoreAction,
                                stageBand = true,
                            )
                        },
                        managerContent = {},
                        viewportContent = {
                            Box(
                                Modifier.weight(1f).fillMaxWidth().background(Color.Black)
                                    .onSizeChanged { viewportSize.value = it },
                            )
                        },
                        footerContent = {
                            FinderLiveDock(
                                query = query.value,
                                onQueryChange = { query.value = it },
                                activeListName =
                                    "Quarterly review list with a long accessible name",
                                activeListTermCount = 4,
                                matchMode = SearchMatchMode.WholeWord,
                                searchListsLabel = "Lists",
                                onSearchLists = ::ignoreAction,
                                onTerms = ::ignoreAction,
                                onUseManual = { useManualClicks.intValue++ },
                                onResults = ::ignoreAction,
                                resultsLabel = "Results unavailable",
                                saving = true,
                                captureLabel = "Freeze frame",
                                captureEnabled = true,
                                onCapture = ::ignoreAction,
                                pendingCaptureLabel = null,
                                onCancelCapture = ::ignoreAction,
                                secondaryActionLabel = null,
                                onSecondaryAction = ::ignoreAction,
                            )
                        },
                    )
                }
            }
        }

        val density = checkNotNull(composeView).resources.displayMetrics.density
        assertEquals(744f, safeSize.value.height / density)
        assertTrue(viewportSize.value.height / density >= 324f)
        val nodes = semanticsNodes()
        assertTrue(
            nodes.any { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text ->
                    text.text.contains("Search list · Quarterly review list")
                }
            },
        )
        assertTrue(
            nodes.any { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text ->
                    text.text.contains("4 terms")
                }
            },
        )
        assertFalse(nodes.any { it.config.contains(SemanticsProperties.EditableText) })
        assertTrue(nodeWithText(nodes, "Saving…").boundsInRoot.height > 0f)
        val useManual = nodeWithText(nodes, "Use manual search", clickable = true)
        assertTouchBounds(useManual)
        assertTrue(checkNotNull(useManual.config.getOrNull(SemanticsActions.OnClick)?.action)())
        assertEquals(1, useManualClicks.intValue)

        val outputDirectory = System.getenv("LTF_VISUAL_OUTPUT_DIR")
        if (outputDirectory != null) {
            val bitmap = captureRenderedBitmap()
            saveRenderedFixture(outputDirectory, "live-dock-list-dark.png", bitmap)
            bitmap.recycle()
            val darkViewport = viewportSize.value
            darkTheme.value = false
            Snapshot.sendApplyNotifications()
            measureCurrentComposition()
            assertEquals(darkViewport, viewportSize.value)
            val lightBitmap = captureRenderedBitmap()
            saveRenderedFixture(outputDirectory, "live-dock-list-light.png", lightBitmap)
            lightBitmap.recycle()
        }
    }

    @Test
    @Config(sdk = [35])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun appKeepsFocusedLiveDockQueryAcrossImeAndLargeFontPolicyChanges() {
        val session = inMemorySearchSessionController()
        session.setManualQuery("invoice-2048")
        val fontScale = mutableFloatStateOf(1f)
        mount(widthDp = 360, heightDp = 800) {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = fontScale.floatValue),
            ) {
                App(session)
            }
        }

        var nodes = semanticsNodes()
        assertTrue(nodes.any { it.config.getOrNull(TestTag) == "finder-image-viewport" })
        var queryNode = nodes.single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == "invoice-2048"
        }
        val requestFocus = checkNotNull(
            queryNode.config.getOrNull(SemanticsActions.RequestFocus)?.action,
        )
        assertTrue(requestFocus())
        val imeInsets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 320))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        ViewCompat.dispatchApplyWindowInsets(checkNotNull(composeView), imeInsets)
        shadowOf(Looper.getMainLooper()).idle()
        measureCurrentComposition()

        nodes = semanticsNodes()
        assertFalse(nodes.any { it.config.getOrNull(TestTag) == "finder-image-viewport" })
        queryNode = nodes.single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == "invoice-2048"
        }
        assertEquals(true, queryNode.config.getOrNull(Focused))
        assertTrue(nodeWithText(nodes, "Options", clickable = true).boundsInRoot.height > 0f)
        assertTrue(nodeWithText(nodes, "Lists", clickable = true).boundsInRoot.height > 0f)

        fontScale.floatValue = 1.5f
        shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
        measureAt(checkNotNull(composeView), checkNotNull(activity), widthDp = 360, heightDp = 400)
        measureCurrentComposition()
        nodes = semanticsNodes()
        assertFalse(nodes.any { it.config.getOrNull(TestTag) == "finder-image-viewport" })
        queryNode = nodes.single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == "invoice-2048"
        }
        assertEquals(true, queryNode.config.getOrNull(Focused))
        assertEquals(1.5f, queryNode.layoutInfo.density.fontScale)
        val scrollNode = checkNotNull(
            nodes.firstOrNull { it.config.contains(SemanticsActions.ScrollBy) },
        )
        val scrollBy = checkNotNull(
            scrollNode.config.getOrNull(SemanticsActions.ScrollBy)?.action,
        )
        assertTrue(scrollBy(0f, -1000f))
        measureCurrentComposition()
        nodes = semanticsNodes()
        queryNode = nodes.single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == "invoice-2048"
        }
        assertEquals(true, queryNode.config.getOrNull(Focused))
        assertTrue(nodeWithText(nodes, "Options", clickable = true).boundsInRoot.height > 0f)
        assertTrue(scrollBy(0f, 1000f))
        measureCurrentComposition()
        nodes = semanticsNodes()
        queryNode = nodes.single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == "invoice-2048"
        }
        assertEquals(true, queryNode.config.getOrNull(Focused))
        assertTrue(nodeWithText(nodes, "Freeze frame").boundsInRoot.height > 0f)
        val setText = checkNotNull(queryNode.config.getOrNull(SemanticsActions.SetText)?.action)
        assertTrue(setText(AnnotatedString("compact query preserved")))
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        assertTrue(
            semanticsNodes().any { node ->
                node.config.getOrNull(SemanticsProperties.EditableText)?.text ==
                    "compact query preserved"
            },
        )
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

    private fun countPixels(bitmap: Bitmap, targetColor: Int): Int {
        var count = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) == targetColor) count++
            }
        }
        return count
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
