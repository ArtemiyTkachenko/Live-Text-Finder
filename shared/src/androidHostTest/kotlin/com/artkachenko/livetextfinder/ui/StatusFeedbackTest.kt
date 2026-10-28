package com.artkachenko.livetextfinder.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.artkachenko.livetextfinder.AppContentColumn
import com.artkachenko.livetextfinder.FinderStatusRegion
import com.artkachenko.livetextfinder.SearchFeedback
import com.artkachenko.livetextfinder.SearchFeedbackKind
import com.artkachenko.livetextfinder.StatusSource
import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.FreezePresentationState
import com.artkachenko.livetextfinder.camera.FrozenSearchFrame
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.ImportedImageState
import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import com.artkachenko.livetextfinder.recognitionDetails
import com.artkachenko.livetextfinder.search.FrameIdentity
import com.artkachenko.livetextfinder.search.Highlights
import com.artkachenko.livetextfinder.search.SearchPresentationOwner
import com.artkachenko.livetextfinder.search.SearchSessionState
import com.artkachenko.livetextfinder.search.searchRecognizedContent
import com.artkachenko.livetextfinder.search.searchRecognizedText
import com.artkachenko.livetextfinder.ui.FinderSearchControls
import com.artkachenko.livetextfinder.ui.sourceStatusMessage
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
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
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatusFeedbackTest {
    private var activity: ComponentActivity? = null
    private var composeView: ComposeView? = null
    private var contentScrollState: ScrollState? = null

    @After
    fun disposeComposition() {
        composeView?.disposeComposition()
        activity?.finish()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun productionDetailsDialogShowsSelectedSourceMetadataAndCloses() {
        val isOpen = mutableStateOf(false)
        val details = recognitionDetails(
            source = StatusSource.ImportedImage,
            content = ImportedTextSnapshot(
                text = "invoice paid",
                lines = listOf(
                    TextLine(
                        text = "invoice paid",
                        bounds = null,
                        corners = emptyList(),
                        elements = listOf(
                            TextElement("invoice", null, emptyList()),
                            TextElement("paid", null, emptyList()),
                        ),
                    ),
                ),
                width = 640,
                height = 480,
                sourceIdentity = 7,
            ),
            outlinedElementCount = 2,
            retainedOutlines = false,
        )
        mount(widthDp = 360, heightDp = 700) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column {
                        StatusSummary(
                            message = sourceStatusMessage(
                                StatusSource.ImportedImage,
                                SearchFeedback(SearchFeedbackKind.Matches, matchCount = 1),
                            ),
                            details = details,
                            onDetails = { isOpen.value = true },
                        )
                        StatusSummary(
                            message = sourceStatusMessage(
                                StatusSource.ImportedImage,
                                SearchFeedback(SearchFeedbackKind.Matches, matchCount = 2),
                            ),
                            details = null,
                            onDetails = ::ignoreAction,
                        )
                    }
                }
                if (isOpen.value) {
                    StatusDetailsDialog(
                        title = "Recognition details",
                        details = details,
                        onDismissRequest = { isOpen.value = false },
                    )
                }
            }
        }

        val summaryNodes = semanticsNodes(composeView)
        assertTrue(summaryNodes.any { it.textValues().contains("Imported image · 1 match") })
        assertTrue(summaryNodes.any { it.textValues().contains("Imported image · 2 matches") })
        val detailsButton = nodeWithText(summaryNodes, "Details")
        assertTouchBounds(detailsButton, composeView)
        click(detailsButton)
        assertTrue(isOpen.value)
        settleDialog()
        measureCurrentComposition()

        val dialog = checkNotNull(ShadowDialog.getLatestDialog())
        val dialogRoot = findViewRoot(dialog.window?.decorView)
        val dialogNodes = semanticsNodes(dialogRoot)
        assertTrue(dialogNodes.any { it.textValues().contains("Recognition details") })
        assertTrue(dialogNodes.any { it.textValues().contains("Source: imported image") })
        assertTrue(dialogNodes.any { it.textValues().contains("Image: 640 × 480 pixels") })
        assertTrue(dialogNodes.any { it.textValues().contains("2 outlined elements") })
        assertTrue(dialogNodes.any { it.textValues().contains("Text sample: invoice paid") })
        assertDialogRendered(dialog, "status-light-imported-details.png")
        val close = nodeWithText(dialogNodes, "Close")
        assertTouchBounds(close, dialog.window?.decorView)
        click(close)
        assertFalse(isOpen.value)
    }

    @Test
    fun contextualStatusWrapsAtLargeFontAndDismissCallbackRuns() {
        val isOpen = mutableStateOf(false)
        var dismissCount = 0
        var retryCount = 0
        val message =
            "Camera is unavailable. Check camera access and retry after returning to the app."
        mount(widthDp = 320, heightDp = 360, fontScale = 1.5f) {
            LiveTextFinderTheme(darkThemeOverride = true, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    ContextMessage(
                        message = message,
                        actionLabel = "Retry",
                        onAction = {
                            retryCount++
                            isOpen.value = true
                        },
                        isError = true,
                    )
                }
                if (isOpen.value) {
                    StatusDetailsDialog(
                        title = "Recognition details",
                        details = recognitionDetails(
                            source = StatusSource.LiveCamera,
                            content = null,
                            outlinedElementCount = null,
                            retainedOutlines = false,
                            sourceMessage = message,
                        ),
                        onDismissRequest = {
                            dismissCount++
                            isOpen.value = false
                        },
                    )
                }
            }
        }

        val nodes = semanticsNodes(composeView)
        val messageNode = nodeWithText(nodes, message)
        val layouts = mutableListOf<TextLayoutResult>()
        val getLayout = checkNotNull(
            messageNode.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action,
        )
        assertTrue(getLayout(layouts))
        assertTrue(layouts.single().lineCount > 1)
        assertFalse(layouts.single().hasVisualOverflow)
        val retry = nodeWithText(nodes, "Retry")
        assertTouchBounds(retry, composeView)
        click(retry)
        assertEquals(1, retryCount)
        settleDialog()
        measureCurrentComposition()
        val dialog = checkNotNull(ShadowDialog.getLatestDialog())
        val dialogNodes = semanticsNodes(findViewRoot(dialog.window?.decorView))
        assertTrue(dialogNodes.any { it.textValues().contains("Recognized text: unavailable") })
        assertTrue(dialogNodes.any { it.textValues().contains(message) })
        assertDialogRendered(dialog, "status-dark-unavailable-details.png")
        checkNotNull(dialog as? ComponentDialog).onBackPressedDispatcher.onBackPressed()
        measureCurrentComposition()
        assertEquals(1, dismissCount)
        assertFalse(isOpen.value)
        assertRendered("status-dark-error-large-text.png")
    }

    @Test
    fun appStatusRegionShowsNonactiveCameraStatesAndImportErrorsTogether() {
        val state = mutableStateOf(OcrUiState(OcrStatus.PermissionRequired))
        val imported = mutableStateOf(
            ImportedImageState(phase = ImportedImagePhase.Error, message = "Image import failed."),
        )
        mount(widthDp = 360, heightDp = 540) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth()) {
                        TestFinderStatusRegion(
                            state = state.value,
                            importedState = imported.value,
                            sessionState = SearchSessionState(loaded = true),
                            shouldShowViews = true,
                        )
                    }
                }
            }
        }

        var nodes = semanticsNodes(composeView)
        assertTrue(
            nodes.any {
                it.textValues().contains("Allow camera access to scan printed Latin text")
            },
        )
        assertTrue(nodes.any { it.textValues().contains("Image import failed.") })

        state.value = OcrUiState(OcrStatus.Starting)
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(nodes.any { it.textValues().contains("Starting camera and text recognition") })
        assertTrue(nodes.any { it.textValues().contains("Image import failed.") })

        state.value = OcrUiState(OcrStatus.Paused)
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(nodes.any { it.textValues().contains("Scanning paused") })

        state.value = OcrUiState(
            OcrStatus.Unavailable,
            "No rear camera is available on this device.",
        )
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(
            nodes.any {
                it.textValues().contains("No rear camera is available on this device.")
            },
        )

        state.value = OcrUiState(OcrStatus.Error, "Camera is unavailable (code 77). Retry.")
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        val errorSummary = nodeWithText(
            nodes,
            "Camera recognition failed. Check camera access and retry the camera.",
        )
        assertTrue(errorSummary.size.width > 0 && errorSummary.size.height > 0)
        assertTrue(nodes.any { it.textValues().contains("Image import failed.") })
        assertFalse(nodes.any { it.textValues().any { text -> text.contains("code 77") } })
        assertTrue(nodes.any { it.textValues().contains("Details") })
        assertRendered("status-live-camera-error.png")
        click(nodeWithText(nodes, "Details"))
        settleDialog()
        val dialog = checkNotNull(ShadowDialog.getLatestDialog())
        val dialogNodes = semanticsNodes(findViewRoot(dialog.window?.decorView))
        assertTrue(
            dialogNodes.any {
                it.textValues().contains("Camera is unavailable (code 77). Retry.")
            },
        )
        click(nodeWithText(dialogNodes, "Close"))
    }

    @Test
    fun appStatusDetailsUseSelectedSourceAndCloseWhenHidden() {
        val liveSnapshot = cameraSnapshot("live-camera-private-token")
        val state = mutableStateOf(OcrUiState(OcrStatus.TextRecognized, snapshot = liveSnapshot))
        val imported = mutableStateOf(
            ImportedImageState(
                phase = ImportedImagePhase.Imported,
                snapshot = importedSnapshot("imported-image-only-text"),
            ),
        )
        val freeze = mutableStateOf(FreezePresentationState())
        val visible = mutableStateOf(true)
        mount(widthDp = 360, heightDp = 600) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth()) {
                        TestFinderStatusRegion(
                            state = state.value,
                            importedState = imported.value,
                            freezeState = freeze.value,
                            sessionState = SearchSessionState(loaded = true),
                            shouldShowViews = visible.value,
                        )
                    }
                }
            }
        }

        var nodes = semanticsNodes(composeView)
        assertTrue(
            nodes.any {
                it.textValues().contains("Imported image · No matches in recognized text")
            },
        )
        click(nodeWithText(nodes, "Details"))
        settleDialog()
        val dialog = checkNotNull(ShadowDialog.getLatestDialog())
        var dialogNodes = semanticsNodes(findViewRoot(dialog.window?.decorView))
        assertTrue(dialogNodes.any { it.textValues().contains("Source: imported image") })
        assertTrue(
            dialogNodes.any {
                it.textValues().contains("Text sample: imported-image-only-text")
            },
        )
        assertFalse(
            dialogNodes.any {
                it.textValues().any { text -> text.contains("live-camera-private") }
            },
        )
        click(nodeWithText(dialogNodes, "Close"))
        assertFalse(dialog.isShowing)

        click(nodeWithText(semanticsNodes(composeView), "Details"))
        settleDialog()
        val reopenedDialog = checkNotNull(ShadowDialog.getLatestDialog())
        assertTrue(reopenedDialog.isShowing)
        visible.value = false
        measureCurrentComposition()
        assertFalse(reopenedDialog.isShowing)
        visible.value = true
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(nodes.any { it.textValues().contains("Details") })
        assertFalse(reopenedDialog.isShowing)

        imported.value = ImportedImageState()
        freeze.value = FreezePresentationState(
            mode = FreezeMode.Frozen,
            frame = FrozenSearchFrame(
                requestId = 9L,
                identity = FrameIdentity(session = 3L, generation = 9L),
                snapshot = cameraSnapshot("frozen-frame-only-text"),
            ),
        )
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        click(nodeWithText(nodes, "Details"))
        settleDialog()
        val frozenDialog = checkNotNull(ShadowDialog.getLatestDialog())
        val frozenNodes = semanticsNodes(findViewRoot(frozenDialog.window?.decorView))
        assertTrue(frozenNodes.any { it.textValues().contains("Source: frozen frame") })
        assertTrue(
            frozenNodes.any { it.textValues().contains("Text sample: frozen-frame-only-text") },
        )
        assertFalse(
            frozenNodes.any {
                it.textValues().any { text -> text.contains("live-camera-private") }
            },
        )
        assertRendered("status-imported-source-selected.png")
    }

    @Test
    fun appStatusRegionGatesRecoveryAndCallsOnlyExistingOperations() {
        val session = mutableStateOf(SearchSessionState(loaded = false))
        var retryCalls = 0
        var resetRequests = 0
        mount(widthDp = 360, heightDp = 540) {
            LiveTextFinderTheme(darkThemeOverride = true, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth()) {
                        TestFinderStatusRegion(
                            state = OcrUiState(OcrStatus.Paused),
                            importedState = ImportedImageState(),
                            sessionState = session.value,
                            shouldShowViews = true,
                            onRetry = { retryCalls++ },
                            onRequestReset = { resetRequests++ },
                        )
                    }
                }
            }
        }
        var nodes = semanticsNodes(composeView)
        assertTrue(nodes.any { it.textValues().contains("Loading saved searches…") })
        assertFalse(nodes.any { it.textValues().contains("Retry") })
        assertFalse(nodes.any { it.textValues().contains("Reset saved data") })

        session.value = SearchSessionState(loaded = true, saving = true)
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(nodes.any { it.textValues().contains("Saving…") })

        session.value = SearchSessionState(
            loaded = true,
            saving = true,
            error = "Saved search write failed.",
        )
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(nodes.any { it.textValues().contains("Saved search write failed. · Saving…") })
        click(nodeWithText(nodes, "Retry"))
        assertEquals(1, retryCalls)

        session.value = SearchSessionState(
            loaded = true,
            error = "Saved search data is unreadable.",
            recoveryRequired = true,
        )
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(
            nodes.any {
                it.textValues().contains(
                    "Saved search data is unreadable.\n" +
                        "Reset clears saved lists, the saved query, and colour.",
                )
            },
        )
        click(nodeWithText(nodes, "Reset saved data"))
        assertEquals(1, resetRequests)
        assertEquals(1, retryCalls)
        assertRendered("status-recovery-confirm-request.png")
    }

    @Test
    fun compactControlsStatusAndWeightedPreviewKeepRecoveryActionReachable() {
        var resetCount = 0
        mount(widthDp = 360, heightDp = 700, fontScale = 1.25f) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    AppContentColumn(
                        scrollState = ScrollState(0),
                        scrollable = false,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(FinderSpacing.small),
                            verticalArrangement = Arrangement.spacedBy(FinderSpacing.small),
                        ) {
                            FinderSearchControls(
                                showTitle = true,
                                query = "invoice",
                                onQueryChange = ::ignoreText,
                                searchListsLabel = "Search lists",
                                onSearchLists = ::ignoreAction,
                                searchContext = "Manual search",
                                highlightLabel = "Highlight color: Yellow",
                                highlightEnabled = true,
                                onHighlight = ::ignoreAction,
                                onHelp = ::ignoreAction,
                                captureLabel = "Freeze frame",
                                captureEnabled = true,
                                onCapture = ::ignoreAction,
                                pendingCaptureLabel = null,
                                onCancelCapture = ::ignoreAction,
                                liveAreaLabel = null,
                                onLiveArea = ::ignoreAction,
                                secondaryActionLabel = "Import image",
                                onSecondaryAction = ::ignoreAction,
                            )
                            TestFinderStatusRegion(
                                state = OcrUiState(
                                    OcrStatus.TextRecognized,
                                    snapshot = cameraSnapshot("invoice invoice"),
                                ),
                                importedState = ImportedImageState(),
                                sessionState = SearchSessionState(recoveryRequired = true),
                                shouldShowViews = true,
                                onRequestReset = { resetCount++ },
                            )
                        }
                        Box(
                            modifier = Modifier.weight(1f)
                                .fillMaxWidth()
                                .background(Color.Black)
                                .semantics { contentDescription = "Preview region" },
                        )
                    }
                }
            }
        }

        val nodes = semanticsNodes(composeView)
        val preview = nodeWithDescription(nodes, "Preview region")
        assertTrue(preview.size.height > 0)
        val recoveryText = nodeWithText(
            nodes,
            "Saved search data needs recovery.\nReset clears saved lists, the saved query, and " +
                "colour.",
        )
        assertTrue(recoveryText.size.width > 0 && recoveryText.size.height > 0)
        assertTextIsLaidOut(recoveryText)
        assertWithinViewport(recoveryText)
        val statusText = nodeWithText(nodes, "Scanning for Latin text · 2 matches")
        assertTrue(preview.positionInRoot.y > recoveryText.positionInRoot.y)
        assertTrue(preview.positionInRoot.y > statusText.positionInRoot.y)
        assertWithinViewport(preview)
        assertRendered("status-light-combined-preview.png")
        val recovery = nodeWithText(nodes, "Reset saved data")
        assertTrue(recovery.size.width > 0 && recovery.size.height > 0)
        assertWithinViewport(recovery)
        assertTouchBounds(recovery, composeView)
        click(recovery)
        assertEquals(1, resetCount)
    }

    @Test
    fun compactRecoveryPageScrollKeepsResetActionReachable() {
        val scrollState = ScrollState(0)
        contentScrollState = scrollState
        var resetCount = 0
        mount(widthDp = 360, heightDp = 300, fontScale = 1.5f) {
            LiveTextFinderTheme(darkThemeOverride = true, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    AppContentColumn(
                        scrollState = scrollState,
                        scrollable = true,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        FinderSearchControls(
                            showTitle = false,
                            query = "invoice",
                            onQueryChange = ::ignoreText,
                            searchListsLabel = "Close lists",
                            onSearchLists = ::ignoreAction,
                            searchContext = "Manual search",
                            highlightLabel = "Highlight color: Yellow",
                            highlightEnabled = false,
                            onHighlight = ::ignoreAction,
                            onHelp = ::ignoreAction,
                            captureLabel = null,
                            captureEnabled = false,
                            onCapture = ::ignoreAction,
                            pendingCaptureLabel = null,
                            onCancelCapture = ::ignoreAction,
                            liveAreaLabel = null,
                            onLiveArea = ::ignoreAction,
                            secondaryActionLabel = "Import image",
                            onSecondaryAction = ::ignoreAction,
                        )
                        repeat(4) { index -> Text("Earlier search setting $index") }
                        TestFinderStatusRegion(
                            state = OcrUiState(OcrStatus.Paused),
                            importedState = ImportedImageState(),
                            sessionState = SearchSessionState(recoveryRequired = true),
                            shouldShowViews = false,
                            onRequestReset = { resetCount++ },
                        )
                        repeat(8) { index -> Text("Saved search item $index") }
                    }
                }
            }
        }
        assertTrue(scrollState.maxValue > 0)
        val beforeScroll = nodeWithText(semanticsNodes(composeView), "Reset saved data")
        val view = checkNotNull(composeView)
        val requiredScroll = (
            scrollState.value + beforeScroll.positionInRoot.y + beforeScroll.size.height -
                view.height / 2f
            ).toInt().coerceIn(0, scrollState.maxValue)
        runBlockingScroll(requiredScroll)
        val reset = nodeWithText(semanticsNodes(composeView), "Reset saved data")
        assertTrue(reset.size.width > 0 && reset.size.height > 0)
        assertWithinViewport(reset)
        assertTouchBounds(reset, composeView)
        click(reset)
        assertEquals(1, resetCount)
        assertRendered("status-dark-recovery-scrolled.png")
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
        activity = hostActivity
        composeView = hostView
        hostView.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    hostActivity.resources.displayMetrics.density,
                    fontScale,
                ),
            ) {
                content()
            }
        }
        hostActivity.setContentView(hostView)
        measureAt(hostView, hostActivity, widthDp, heightDp)
        shadowOf(Looper.getMainLooper()).idle()
        measureAt(hostView, hostActivity, widthDp, heightDp)
        (hostView.getChildAt(0) as ViewRootForTest).measureAndLayoutForTest()
    }

    @Composable
    private fun TestFinderStatusRegion(
        state: OcrUiState,
        importedState: ImportedImageState,
        sessionState: SearchSessionState,
        shouldShowViews: Boolean,
        freezeState: FreezePresentationState = FreezePresentationState(),
        onRetry: () -> Unit = ::ignoreAction,
        onRequestReset: () -> Unit = ::ignoreAction,
    ) {
        val liveSearch = searchRecognizedText("invoice", state)
        val search = when {
            importedState.phase == ImportedImagePhase.Imported ->
                searchRecognizedContent(liveSearch.spec, importedState.snapshot)
            freezeState.mode == FreezeMode.Frozen ->
                searchRecognizedContent(liveSearch.spec, freezeState.frame?.snapshot)
            else -> liveSearch
        }
        val presentation = SearchPresentationOwner().present(search.spec, state, 0L)
        FinderStatusRegion(
            state = state,
            importedState = importedState,
            freezeState = freezeState,
            sessionState = sessionState,
            search = search,
            presentation = presentation,
            highlights = Highlights(ready = true, outlines = emptyList()),
            areaSelected = false,
            shouldShowViews = shouldShowViews,
            onRetrySavedData = onRetry,
            onRequestReset = onRequestReset,
        )
    }

    private fun importedSnapshot(text: String): ImportedTextSnapshot = ImportedTextSnapshot(
        text = text,
        lines = listOf(
            TextLine(
                text = text,
                bounds = null,
                corners = emptyList(),
                elements = listOf(TextElement(text, null, emptyList())),
            ),
        ),
        width = 640,
        height = 480,
        sourceIdentity = 17,
    )

    private fun cameraSnapshot(text: String): OcrSnapshot = OcrSnapshot(
        text = text,
        lines = listOf(
            TextLine(
                text = text,
                bounds = null,
                corners = emptyList(),
                elements = listOf(TextElement(text, null, emptyList())),
            ),
        ),
        frame = FrameInfo(
            timestampNanos = 1L,
            bufferWidth = 640,
            bufferHeight = 480,
            rotationDegrees = 0,
            crop = TextBounds(0, 0, 640, 480),
            sensorToBufferMatrix = emptyList(),
        ),
    )

    private fun measureAt(view: ComposeView, hostActivity: Activity, widthDp: Int, heightDp: Int) {
        val density = hostActivity.resources.displayMetrics.density
        val widthPx = (widthDp * density).roundToInt()
        val heightPx = (heightDp * density).roundToInt()
        view.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, widthPx, heightPx)
    }

    private fun semanticsNodes(view: View?): List<SemanticsNode> =
        semanticsNodes(findViewRoot(view))

    private fun semanticsNodes(root: ViewRootForTest): List<SemanticsNode> {
        val nodes = mutableListOf<SemanticsNode>()
        fun collect(node: SemanticsNode) {
            nodes += node
            node.children.forEach(::collect)
        }
        collect(root.semanticsOwner.rootSemanticsNode)
        return nodes
    }

    private fun findViewRoot(view: View?): ViewRootForTest = when (view) {
        is ViewRootForTest -> view
        is ViewGroup -> (0 until view.childCount)
            .asSequence()
            .mapNotNull { index ->
                runCatching { findViewRoot(view.getChildAt(index)) }.getOrNull()
            }
            .firstOrNull()
            ?: error("No Compose view root found")
        else -> error("No Compose view root found")
    }

    private fun nodeWithText(nodes: List<SemanticsNode>, text: String): SemanticsNode =
        checkNotNull(nodes.firstOrNull { it.textValues().contains(text) }) {
            "No semantics node with text '$text'"
        }

    private fun nodeWithDescription(
        nodes: List<SemanticsNode>,
        description: String,
    ): SemanticsNode = checkNotNull(
        nodes.firstOrNull {
            it.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.contains(description) == true
        },
    ) {
        "No semantics node with description '$description'"
    }

    private fun SemanticsNode.textValues(): List<String> =
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    private fun assertTouchBounds(node: SemanticsNode, view: View?) {
        val density = checkNotNull(view).resources.displayMetrics.density
        assertTrue(node.touchBoundsInRoot.width / density >= 48f)
        assertTrue(node.touchBoundsInRoot.height / density >= 48f)
    }

    private fun assertTextIsLaidOut(node: SemanticsNode) {
        val layouts = mutableListOf<TextLayoutResult>()
        val getLayout = checkNotNull(
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action,
        )
        assertTrue(getLayout(layouts))
        assertTrue(layouts.single().lineCount > 0)
        assertFalse(layouts.single().hasVisualOverflow)
    }

    private fun click(node: SemanticsNode) {
        assertTrue(checkNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.action)())
        measureCurrentComposition()
    }

    private fun runBlockingScroll(value: Int) {
        runBlocking {
            checkNotNull(contentScrollState).scrollTo(value)
        }
        measureCurrentComposition()
    }

    private fun assertWithinViewport(node: SemanticsNode) {
        val view = checkNotNull(composeView)
        val position = node.positionInRoot
        assertTrue(position.x >= 0f && position.y >= 0f)
        assertTrue(position.x + node.size.width <= view.width)
        assertTrue(position.y + node.size.height <= view.height)
    }

    private fun settleDialog() {
        repeat(3) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            measureHostRoot()
            ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView?.let {
                findViewRoot(it)?.measureAndLayoutForTest()
            }
        }
    }

    private fun measureCurrentComposition() {
        repeat(3) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            measureHostRoot()
        }
    }

    private fun measureHostRoot() {
        (checkNotNull(composeView).getChildAt(0) as ViewRootForTest).measureAndLayoutForTest()
    }

    private fun assertRendered(fileName: String) {
        val view = checkNotNull(composeView)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        System.getenv("LTF_VISUAL_OUTPUT_DIR")?.let { directory ->
            val output = File(directory, fileName)
            output.parentFile?.mkdirs()
            FileOutputStream(output).use { stream ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
        bitmap.recycle()
    }

    private fun assertDialogRendered(dialog: android.app.Dialog, fileName: String) {
        val view = checkNotNull(dialog.window?.decorView)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        System.getenv("LTF_VISUAL_OUTPUT_DIR")?.let { directory ->
            val output = File(directory, fileName)
            output.parentFile?.mkdirs()
            FileOutputStream(output).use { stream ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
        bitmap.recycle()
    }

    private fun ignoreAction() = Unit

    private fun ignoreText(value: String) = Unit

}
