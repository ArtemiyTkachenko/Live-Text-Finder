package com.artkachenko.livetextfinder.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.camera.view.PreviewView
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
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.lifecycle.MutableLiveData
import com.artkachenko.livetextfinder.AppContentColumn
import com.artkachenko.livetextfinder.FinderStatusRegion
import com.artkachenko.livetextfinder.SearchFeedback
import com.artkachenko.livetextfinder.SearchFeedbackKind
import com.artkachenko.livetextfinder.StatusSource
import com.artkachenko.livetextfinder.camera.CameraPreviewFeedbackLayer
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
import com.artkachenko.livetextfinder.search.HighlightColor
import com.artkachenko.livetextfinder.search.Highlights
import com.artkachenko.livetextfinder.search.InMemorySearchSessionStore
import com.artkachenko.livetextfinder.search.PersistedSearchState
import com.artkachenko.livetextfinder.search.SavedSearchList
import com.artkachenko.livetextfinder.search.SearchListEditorState
import com.artkachenko.livetextfinder.search.SearchMatchMode
import com.artkachenko.livetextfinder.search.SearchPresentationOwner
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.SearchSessionState
import com.artkachenko.livetextfinder.search.SearchSessionStore
import com.artkachenko.livetextfinder.search.SearchTermSummary
import com.artkachenko.livetextfinder.search.dispatchSearchListSelection
import com.artkachenko.livetextfinder.search.searchRecognizedContent
import com.artkachenko.livetextfinder.search.searchRecognizedText
import com.artkachenko.livetextfinder.ui.FinderSearchControls
import com.artkachenko.livetextfinder.ui.FinderSearchLists
import com.artkachenko.livetextfinder.ui.HighlightColorDialog
import com.artkachenko.livetextfinder.ui.SearchTermResultsDialog
import com.artkachenko.livetextfinder.ui.sourceStatusMessage
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

private const val MAX_RECOMPOSITION_FRAMES = 32

private object ImmediateMotionDurationScale : MotionDurationScale {
    override val scaleFactor: Float = 0f
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatusFeedbackTest {
    private var activity: ComponentActivity? = null
    private var composeView: ComposeView? = null
    private var contentScrollState: ScrollState? = null
    private var frameClock: BroadcastFrameClock? = null
    private var recomposer: Recomposer? = null
    private var recomposerScope: CoroutineScope? = null
    private var nextFrameTimeNanos = 1_000_000_000L

    @After
    fun disposeComposition() {
        composeView?.disposeComposition()
        ShadowDialog.getLatestDialog()?.dismiss()
        recomposer?.cancel()
        recomposerScope?.cancel()
        activity?.finish()
        shadowOf(Looper.getMainLooper()).idle()
        frameClock = null
        recomposer = null
        recomposerScope = null
    }

    @Test
    fun cameraPreviewFeedbackTracksStreamWithoutChangingViewportBounds() {
        val streamState = MutableLiveData(PreviewView.StreamState.IDLE)
        val ocrState = OcrUiState(OcrStatus.Starting)
        mount(widthDp = 320, heightDp = 180) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = "Preview viewport" },
                ) {
                    CameraPreviewFeedbackLayer(
                        streamState,
                        failed = false,
                        onRetry = ::ignoreAction,
                    )
                }
            }
        }

        var nodes = semanticsNodes(composeView)
        assertEquals(OcrStatus.Starting, ocrState.status)
        assertTrue(nodes.any { it.textValues().contains("Starting camera…") })
        assertFalse(nodes.any { it.textValues().contains("Retry camera") })
        val viewportBounds = layoutBounds(nodeWithDescription(nodes, "Preview viewport"))
        assertRendered("camera-feedback-starting-light.png")

        streamState.value = PreviewView.StreamState.STREAMING
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertFalse(nodes.any { it.textValues().contains("Starting camera…") })
        assertEquals(viewportBounds, layoutBounds(nodeWithDescription(nodes, "Preview viewport")))
        assertRendered("camera-feedback-streaming-light.png")

        streamState.value = PreviewView.StreamState.IDLE
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(nodes.any { it.textValues().contains("Camera paused") })
        assertEquals(viewportBounds, layoutBounds(nodeWithDescription(nodes, "Preview viewport")))
        assertRendered("camera-feedback-paused-light.png")
    }

    @Test
    fun cameraPreviewRetryPrecedesFeedbackAndStaysReachableWithDarkLargeText() {
        val oldStreamState = MutableLiveData(PreviewView.StreamState.IDLE)
        val streamState = mutableStateOf(oldStreamState)
        val failed = mutableStateOf(false)
        var retryCount = 0
        mount(widthDp = 320, heightDp = 180, fontScale = 1.5f) {
            LiveTextFinderTheme(darkThemeOverride = true, dynamicColor = false) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = "Preview viewport" },
                ) {
                    CameraPreviewFeedbackLayer(streamState.value, failed.value) {
                        retryCount++
                        streamState.value = MutableLiveData(PreviewView.StreamState.IDLE)
                        failed.value = false
                    }
                }
            }
        }

        var nodes = semanticsNodes(composeView)
        assertTrue(nodes.any { it.textValues().contains("Starting camera…") })
        val viewportBounds = layoutBounds(nodeWithDescription(nodes, "Preview viewport"))
        assertRendered("camera-feedback-starting-dark-large-text.png")

        oldStreamState.value = PreviewView.StreamState.STREAMING
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertFalse(nodes.any { it.textValues().contains("Starting camera…") })
        oldStreamState.value = PreviewView.StreamState.IDLE
        measureCurrentComposition()
        assertTrue(semanticsNodes(composeView).any { it.textValues().contains("Camera paused") })

        failed.value = true
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertFalse(nodes.any { it.textValues().contains("Starting camera…") })
        val retry = nodeWithText(nodes, "Retry camera", clickable = true)
        assertTouchBounds(retry)
        val density = checkNotNull(composeView).resources.displayMetrics.density
        assertTrue(retry.size.width / density >= 48f)
        assertTrue(retry.size.height / density >= 48f)
        assertRendered("camera-feedback-retry-dark-large-text.png")
        click(retry)
        assertEquals(1, retryCount)
        measureCurrentComposition()
        val replacementStream = streamState.value
        assertFalse(oldStreamState.hasObservers())
        assertTrue(replacementStream.hasObservers())
        nodes = semanticsNodes(composeView)
        assertTrue(nodes.any { it.textValues().contains("Starting camera…") })
        assertFalse(nodes.any { it.textValues().contains("Retry camera") })
        assertRendered("camera-feedback-retry-reset-dark-large-text.png")

        oldStreamState.value = PreviewView.StreamState.STREAMING
        oldStreamState.value = PreviewView.StreamState.IDLE
        measureCurrentComposition()
        assertTrue(semanticsNodes(composeView).any { it.textValues().contains("Starting camera…") })

        replacementStream.value = PreviewView.StreamState.STREAMING
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertFalse(nodes.any { it.textValues().contains("Starting camera…") })
        assertFalse(nodes.any { it.textValues().contains("Camera paused") })

        failed.value = true
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(nodes.any { it.textValues().contains("Retry camera") })
        assertEquals(viewportBounds, layoutBounds(nodeWithDescription(nodes, "Preview viewport")))
    }

    @Test
    fun optionsActionOpensScrollableSheetAndClosesBeforeInvokingSelection() {
        val optionsOpen = mutableStateOf(false)
        val highlightCalls = mutableIntStateOf(0)
        val matchModeCalls = mutableIntStateOf(0)
        val matchMode = mutableStateOf(SearchMatchMode.Substring)
        val matchModeEnabled = mutableStateOf(true)
        val areaCalls = mutableIntStateOf(0)
        val importCalls = mutableIntStateOf(0)
        val helpCalls = mutableIntStateOf(0)
        val dismissCalls = mutableIntStateOf(0)
        val highlightEnabled = mutableStateOf(true)
        val showImport = mutableStateOf(true)
        mount(widthDp = 320, heightDp = 360, fontScale = 1.4f) {
            LiveTextFinderTheme(darkThemeOverride = true, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column {
                        FinderSearchControls(
                            showTitle = false,
                            query = "invoice",
                            onQueryChange = ::ignoreText,
                            searchListsLabel = "Search lists",
                            onSearchLists = ::ignoreAction,
                            searchContext = "Manual search",
                            onOptions = { optionsOpen.value = true },
                            savingLabel = "Saving…",
                            captureLabel = null,
                            captureEnabled = false,
                            onCapture = ::ignoreAction,
                            pendingCaptureLabel = null,
                            onCancelCapture = ::ignoreAction,
                            secondaryActionLabel = null,
                            onSecondaryAction = ::ignoreAction,
                        )
                    }
                }
                if (optionsOpen.value) {
                    FinderOptionsSheet(
                        highlightLabel = "Highlight color: Yellow",
                        highlightEnabled = highlightEnabled.value,
                        matchMode = matchMode.value,
                        matchModeEnabled = matchModeEnabled.value,
                        onMatchMode = {
                            matchMode.value = it
                            matchModeCalls.intValue++
                        },
                        liveAreaLabel = "Live area: Full frame",
                        showImport = showImport.value,
                        showListTransfer = false,
                        listTransferHelp = null,
                        importListEnabled = false,
                        exportListEnabled = false,
                        onImportList = ::ignoreAction,
                        onExportList = ::ignoreAction,
                        onHighlight = {
                            optionsOpen.value = false
                            highlightCalls.intValue++
                        },
                        onLiveArea = {
                            optionsOpen.value = false
                            areaCalls.intValue++
                        },
                        onImport = {
                            optionsOpen.value = false
                            importCalls.intValue++
                        },
                        onHelp = {
                            optionsOpen.value = false
                            helpCalls.intValue++
                        },
                        onDismissRequest = {
                            optionsOpen.value = false
                            dismissCalls.intValue++
                        },
                    )
                }
            }
        }

        assertTrue(semanticsNodes(composeView).any { it.textValues().contains("Saving…") })
        assertRendered("status-dark-compact-saving-caption.png")
        openOptionsSheet()
        var nodes = optionsDialogNodes()
        assertTouchBounds(
            nodeWithText(nodes, "Highlight color: Yellow", clickable = true),
            optionsDialog().window?.decorView,
        )
        assertTrue(nodes.any { it.textValues().contains("Live area: Full frame") })
        assertTrue(nodes.any { it.textValues().contains("Import image") })
        assertTrue(nodes.any { it.textValues().contains("Help") })
        assertTouchBounds(
            nodeWithText(nodes, "Substring", clickable = true),
            optionsDialog().window?.decorView,
        )
        assertTouchBounds(
            nodeWithText(nodes, "Whole words", clickable = true),
            optionsDialog().window?.decorView,
        )
        val substringNode = nodeWithText(nodes, "Substring", clickable = true)
        val wholeWordsNode = nodeWithText(nodes, "Whole words", clickable = true)
        val density = checkNotNull(optionsDialog().window?.decorView)
            .resources.displayMetrics.density
        assertTrue(substringNode.size.height / density >= 48f)
        assertTrue(wholeWordsNode.size.height / density >= 48f)
        assertEquals(true, substringNode.config.getOrNull(SemanticsProperties.Selected))
        assertEquals(false, wholeWordsNode.config.getOrNull(SemanticsProperties.Selected))
        assertTouchBounds(
            nodeWithText(nodes, "Live area: Full frame", clickable = true),
            optionsDialog().window?.decorView,
        )
        assertTouchBounds(
            nodeWithText(nodes, "Import image", clickable = true),
            optionsDialog().window?.decorView,
        )
        val scrollViewport = nodes.firstOrNull {
            it.config.getOrNull(SemanticsActions.ScrollBy) != null
        }
        assertTrue(scrollViewport != null)
        val helpTopBeforeScroll =
            nodeWithText(nodes, "Help", clickable = true).positionInRoot.y
        val scrollBy = checkNotNull(
            scrollViewport?.config?.getOrNull(SemanticsActions.ScrollBy)?.action,
        )
        assertTrue(scrollBy(0f, 320f))
        measureCurrentComposition()
        nodes = optionsDialogNodes()
        val helpAfterScroll = nodeWithText(nodes, "Help", clickable = true)
        assertTrue(helpAfterScroll.positionInRoot.y < helpTopBeforeScroll)
        val firstDialog = optionsDialog()
        val helpViewport = checkNotNull(scrollViewport)
        assertFullyVisibleInViewport(helpAfterScroll, helpViewport)
        val firstDialogView = checkNotNull(firstDialog.window?.decorView)
        assertFullyVisibleInDialog(helpAfterScroll, firstDialogView)
        assertTouchBounds(helpAfterScroll, firstDialog.window?.decorView)
        assertDialogRendered(firstDialog, "options-sheet-dark-compact.png")

        click(nodeWithText(nodes, "Whole words", clickable = true))
        assertEquals(1, matchModeCalls.intValue)
        nodes = optionsDialogNodes()
        assertEquals(
            true,
            nodeWithText(nodes, "Whole words", clickable = true).config.getOrNull(
                SemanticsProperties.Selected,
            ),
        )

        click(nodeWithText(nodes, "Live area: Full frame", clickable = true))
        assertEquals(1, areaCalls.intValue)
        assertEquals(0, importCalls.intValue)
        assertEquals(0, helpCalls.intValue)
        assertEquals(0, dismissCalls.intValue)
        assertFalse(optionsOpen.value)
        openOptionsSheet()
        click(nodeWithText(optionsDialogNodes(), "Import image", clickable = true))
        assertEquals(1, importCalls.intValue)
        assertFalse(optionsOpen.value)
        openOptionsSheet()
        click(nodeWithText(optionsDialogNodes(), "Help", clickable = true))
        assertEquals(1, helpCalls.intValue)
        assertFalse(optionsOpen.value)
        openOptionsSheet()
        click(nodeWithText(optionsDialogNodes(), "Highlight color: Yellow", clickable = true))
        assertEquals(1, highlightCalls.intValue)
        assertEquals(false, optionsOpen.value)
        nodes = semanticsNodes(composeView)
        assertFalse(nodes.any { it.textValues().contains("Live area: Full frame") })
        highlightEnabled.value = false
        matchModeEnabled.value = false
        showImport.value = false
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        openOptionsSheet()
        nodes = optionsDialogNodes()
        assertTrue(
            nodeWithText(nodes, "Highlight color: Yellow")
                .config.contains(SemanticsProperties.Disabled),
        )
        assertTrue(
            nodeWithText(nodes, "Whole words")
                .config.contains(SemanticsProperties.Disabled),
        )
        assertFalse(nodes.any { it.textValues().contains("Import image") })
        val disabledHighlight = nodeWithText(nodes, "Highlight color: Yellow")
        dispatchTouch(
            findComposeView(optionsDialog().window?.decorView),
            disabledHighlight.touchBoundsInRoot,
        )
        measureCurrentComposition()
        assertEquals(1, highlightCalls.intValue)
        val disabledMatchMode = nodeWithText(nodes, "Whole words")
        dispatchTouch(
            findComposeView(optionsDialog().window?.decorView),
            disabledMatchMode.touchBoundsInRoot,
        )
        measureCurrentComposition()
        assertEquals(1, matchModeCalls.intValue)
        checkNotNull(optionsDialog() as? ComponentDialog)
            .onBackPressedDispatcher.onBackPressed()
        advanceFramesUntil { !optionsOpen.value }
        assertEquals(1, dismissCalls.intValue)
        assertEquals(1, highlightCalls.intValue)
        assertFalse(optionsOpen.value)
    }

    @Test
    fun listTransferActionsKeepActualBoundsAndTermsOnlyHelpAtLargeText() {
        val optionsOpen = mutableStateOf(false)
        val transferHelp =
            "Search list text files contain one term or phrase per line. " +
                "Export includes the selected list terms only; colours and search settings " +
                "are not included."
        mount(widthDp = 320, heightDp = 360, fontScale = 1.4f) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column {
                        FinderSearchControls(
                            showTitle = false,
                            query = "invoice",
                            onQueryChange = ::ignoreText,
                            searchListsLabel = "Search lists",
                            onSearchLists = ::ignoreAction,
                            searchContext = "Manual search",
                            onOptions = { optionsOpen.value = true },
                            captureLabel = null,
                            captureEnabled = false,
                            onCapture = ::ignoreAction,
                            pendingCaptureLabel = null,
                            onCancelCapture = ::ignoreAction,
                            secondaryActionLabel = null,
                            onSecondaryAction = ::ignoreAction,
                        )
                    }
                }
                if (optionsOpen.value) {
                    FinderOptionsSheet(
                        highlightLabel = "Highlight color: Yellow",
                        highlightEnabled = true,
                        matchMode = SearchMatchMode.Substring,
                        matchModeEnabled = true,
                        onMatchMode = ::ignoreMatchMode,
                        liveAreaLabel = null,
                        showImport = true,
                        showListTransfer = true,
                        listTransferHelp = transferHelp,
                        importListEnabled = true,
                        exportListEnabled = true,
                        onImportList = ::ignoreAction,
                        onExportList = ::ignoreAction,
                        onHighlight = ::ignoreAction,
                        onLiveArea = ::ignoreAction,
                        onImport = ::ignoreAction,
                        onHelp = ::ignoreAction,
                        onDismissRequest = { optionsOpen.value = false },
                    )
                }
            }
        }

        openOptionsSheet()
        val initialNodes = optionsDialogNodes()
        val viewport = checkNotNull(initialNodes.firstOrNull {
            it.config.getOrNull(SemanticsActions.ScrollBy) != null
        })
        checkNotNull(viewport.config.getOrNull(SemanticsActions.ScrollBy)?.action)
            .invoke(0f, 1_600f)
        measureCurrentComposition()
        val nodes = optionsDialogNodes()
        val dialog = optionsDialog()
        val dialogView = checkNotNull(dialog.window?.decorView)
        val density = dialogView.resources.displayMetrics.density
        val scrollViewport = checkNotNull(nodes.firstOrNull {
            it.config.getOrNull(SemanticsActions.ScrollBy) != null
        })
        listOf("Import search list", "Export selected list").forEach { label ->
            val action = nodeWithText(nodes, label, clickable = true)
            assertTrue(action.size.width / density >= 48f)
            assertTrue(action.size.height / density >= 48f)
            assertFullyVisibleInViewport(action, scrollViewport)
            assertFullyVisibleInDialog(action, dialogView)
        }
        val help = nodeWithText(nodes, transferHelp)
        assertFullyVisibleInViewport(help, scrollViewport)
        assertFullyVisibleInDialog(help, dialogView)
        assertDialogRendered(dialog, "options-list-transfer-large-text.png")
    }

    @Test
    fun lightOptionsSheetRendersInItsNativeWindowAndClosesOnHelp() {
        val optionsOpen = mutableStateOf(false)
        var helpCalls = 0
        mount(widthDp = 360, heightDp = 640) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    FinderSearchControls(
                        showTitle = true,
                        query = "invoice",
                        onQueryChange = ::ignoreText,
                        searchListsLabel = "Search lists",
                        onSearchLists = ::ignoreAction,
                        searchContext = "Manual search",
                        onOptions = { optionsOpen.value = true },
                        captureLabel = "Freeze frame",
                        captureEnabled = true,
                        onCapture = ::ignoreAction,
                        pendingCaptureLabel = null,
                        onCancelCapture = ::ignoreAction,
                        secondaryActionLabel = null,
                        onSecondaryAction = ::ignoreAction,
                    )
                }
                if (optionsOpen.value) {
                    FinderOptionsSheet(
                        highlightLabel = "Highlight color: Yellow",
                        highlightEnabled = true,
                        matchMode = SearchMatchMode.Substring,
                        matchModeEnabled = true,
                        onMatchMode = { },
                        liveAreaLabel = null,
                        showImport = false,
                        showListTransfer = false,
                        listTransferHelp = null,
                        importListEnabled = false,
                        exportListEnabled = false,
                        onImportList = ::ignoreAction,
                        onExportList = ::ignoreAction,
                        onHighlight = { optionsOpen.value = false },
                        onLiveArea = ::ignoreAction,
                        onImport = ::ignoreAction,
                        onHelp = {
                            helpCalls++
                            optionsOpen.value = false
                        },
                        onDismissRequest = { optionsOpen.value = false },
                    )
                }
            }
        }

        openOptionsSheet()
        val dialog = optionsDialog()
        val nodes = optionsDialogNodes()
        assertTrue(nodes.any { it.textValues().contains("Highlight color: Yellow") })
        assertTrue(nodes.any { it.textValues().contains("Help") })
        assertFalse(nodes.any { it.textValues().contains("Import image") })
        assertDialogRendered(dialog, "options-sheet-light.png")
        click(nodeWithText(nodes, "Help", clickable = true))
        assertEquals(1, helpCalls)
        assertFalse(optionsOpen.value)
    }

    @Test
    fun directFreezeAndImportedSourceActionsRemainVisibleAndInvokeOnce() {
        val stage = mutableIntStateOf(0)
        val calls = mutableListOf<String>()
        mount(widthDp = 360, heightDp = 640) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    FinderSearchControls(
                        showTitle = true,
                        query = "invoice",
                        onQueryChange = ::ignoreText,
                        searchListsLabel = "Search lists",
                        onSearchLists = ::ignoreAction,
                        searchContext = "Manual search",
                        onOptions = ::ignoreAction,
                        captureLabel = null,
                        captureEnabled = false,
                        onCapture = ::ignoreAction,
                        pendingCaptureLabel = if (stage.intValue == 0) "Cancel freeze" else null,
                        onCancelCapture = {
                            calls += "freeze"
                            stage.intValue = 1
                        },
                        secondaryActionLabel = when (stage.intValue) {
                            1 -> "Cancel import"
                            2 -> "Return to camera"
                            else -> null
                        },
                        onSecondaryAction = {
                            when (stage.intValue) {
                                1 -> calls += "cancel import"
                                2 -> calls += "return to camera"
                            }
                            stage.intValue++
                        },
                    )
                }
            }
        }

        var nodes = semanticsNodes(composeView)
        val cancelFreeze = nodeWithText(nodes, "Cancel freeze", clickable = true)
        assertTouchBounds(cancelFreeze, composeView)
        click(cancelFreeze)
        assertEquals(listOf("freeze"), calls)
        nodes = semanticsNodes(composeView)
        val cancelImport = nodeWithText(nodes, "Cancel import", clickable = true)
        assertTouchBounds(cancelImport, composeView)
        click(cancelImport)
        assertEquals(listOf("freeze", "cancel import"), calls)
        nodes = semanticsNodes(composeView)
        val returnToCamera = nodeWithText(nodes, "Return to camera", clickable = true)
        assertTouchBounds(returnToCamera, composeView)
        click(returnToCamera)
        assertEquals(listOf("freeze", "cancel import", "return to camera"), calls)
    }

    @Test
    fun listEditorPreviewTracksDraftAndInvalidSaveThenCancelKeepsValidationAuthoritative() {
        val session = SearchSessionController(InMemorySearchSessionStore())
        val editor = mutableStateOf(
            SearchListEditorState(
                isOpen = true,
                name = "Repeated terms",
            ),
        )
        val editorScroll = ScrollState(0)
        contentScrollState = editorScroll
        var cancelCount = 0
        mount(widthDp = 360, heightDp = 700, fontScale = 1.25f) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    AppContentColumn(
                        scrollState = editorScroll,
                        scrollable = true,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        FinderSearchLists(
                            lists = emptyList(),
                            activeListId = null,
                            editor = editor.value,
                            canSave = session.state.value.loaded,
                            onNew = ::ignoreAction,
                            onSelect = ::ignoreText,
                            onEdit = ::ignoreList,
                            onDelete = ::ignoreList,
                            onNameChange = { editor.value = editor.value.copy(name = it) },
                            onTermsChange = { editor.value = editor.value.copy(terms = it) },
                            onSave = {
                                val error = session.saveList(
                                    null,
                                    editor.value.name,
                                    editor.value.terms,
                                )
                                editor.value = if (error == null) {
                                    editor.value.close()
                                } else {
                                    editor.value.copy(error = error)
                                }
                            },
                            onCancel = {
                                cancelCount++
                                editor.value = editor.value.close()
                            },
                        )
                    }
                }
            }
        }

        var nodes = semanticsNodes(composeView)
        assertTrue(nodes.any { it.textValues().contains("0 of 32 terms") })
        assertTrue(
            nodes.any {
                it.textValues().contains("No terms yet. Enter one or more lines above.")
            },
        )
        val emptyTerms = nodes.single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == ""
        }
        val setEmptyTerms = checkNotNull(
            emptyTerms.config.getOrNull(SemanticsActions.SetText)?.action,
        )
        assertTrue(setEmptyTerms(AnnotatedString("invoice\ninvoice")))
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(nodes.any { it.textValues().contains("2 of 32 terms") })
        assertTrue(nodes.any { it.textValues().contains("1. invoice") })
        assertTrue(nodes.any { it.textValues().contains("2. invoice") })
        var save = scrollTextIntoViewport(semanticsNodes(composeView), "Save list")
        assertTouchBounds(save, composeView)
        click(save)
        nodes = semanticsNodes(composeView)
        assertTrue(nodes.any { it.textValues().contains("Terms must be unique, ignoring case.") })
        assertTrue(
            nodes.any {
                it.config.getOrNull(SemanticsProperties.EditableText)?.text ==
                    "invoice\ninvoice"
            },
        )

        val termsField = nodes.single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == "invoice\ninvoice"
        }
        val setTerms = checkNotNull(termsField.config.getOrNull(SemanticsActions.SetText)?.action)
        val longDraft = (1..7).joinToString("\n") {
            "term $it"
        }
        assertTrue(setTerms(AnnotatedString(longDraft)))
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(nodes.any { it.textValues().contains("7 of 32 terms") })
        assertTrue(nodes.any { it.textValues().contains("1. term 1") })
        assertTrue(nodes.any { it.textValues().contains("5. term 5") })
        assertTrue(nodes.any { it.textValues().contains("and 2 more") })

        val updatedTerms = nodes.single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == longDraft
        }
        val setBlankDraft = checkNotNull(
            updatedTerms.config.getOrNull(SemanticsActions.SetText)?.action,
        )
        assertTrue(setBlankDraft(AnnotatedString("invoice\n\ninvoice")))
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(
            nodes.any {
                it.textValues().contains("Blank lines cannot be saved. Remove them to continue.")
            },
        )
        assertTrue(nodes.any { it.textValues().contains("2 of 32 terms") })

        val cancel = scrollTextIntoViewport(nodes, "Cancel")
        assertTouchBounds(cancel, composeView)
        click(cancel)
        assertEquals(1, cancelCount)
        assertFalse(semanticsNodes(composeView).any { it.textValues().contains("Save list") })
        assertTrue(session.state.value.persisted.lists.isEmpty())

        editor.value = editor.value.startNew().copy(
            name = "Normalized draft",
            terms = "  red apple,  ripe  \ninvoice",
        )
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(nodes.any { it.textValues().contains("1. red apple, ripe") })
        assertTrue(nodes.any { it.textValues().contains("2. invoice") })
        nodes = semanticsNodes(composeView)
        save = scrollTextIntoViewport(nodes, "Save list")
        assertTouchBounds(save, composeView)
        click(save)
        assertEquals(
            listOf("red apple, ripe", "invoice"),
            session.state.value.activeList?.terms,
        )
        assertFalse(semanticsNodes(composeView).any { it.textValues().contains("Save list") })
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
    fun productionHighlightPaletteHasSeparatedTouchRowsAndSelectsOnce() {
        val selectedColor = mutableStateOf(HighlightColor.Magenta)
        val selectedColors = mutableListOf<HighlightColor>()
        mount(widthDp = 320, heightDp = 640, fontScale = 1.5f) {
            LiveTextFinderTheme(darkThemeOverride = true, dynamicColor = false) {
                HighlightColorDialog(
                    selectedColor = selectedColor.value,
                    enabled = true,
                    onSelectColor = { color ->
                        selectedColors += color
                        selectedColor.value = color
                    },
                    onDismissRequest = ::ignoreAction,
                )
            }
        }
        settleDialog()

        val dialog = checkNotNull(ShadowDialog.getLatestDialog())
        val dialogRoot = findViewRoot(dialog.window?.decorView)
        var nodes = semanticsNodes(dialogRoot)
        assertTrue(nodes.any { it.textValues().contains("Highlight color") })
        val rowDescriptions = HighlightColor.entries.map { it.displayName }
        val rows = rowDescriptions.map { description ->
            nodeWithDescription(nodes, description)
        }
        rows.forEach { row -> assertTouchBounds(row, dialog.window?.decorView) }
        rows.forEachIndexed { index, row ->
            rows.drop(index + 1).forEach { other ->
                assertFalse(row.touchBoundsInRoot.intersects(other.touchBoundsInRoot))
            }
        }
        val selectedRow = nodeWithSelectedDescription(nodes, "Magenta")
        assertEquals(true, selectedRow.config.getOrNull(SemanticsProperties.Selected))
        assertDialogRendered(dialog, "highlight-palette-dark-narrow-large-text.png")

        HighlightColor.entries.forEach { color ->
            click(nodeWithDescription(nodes, color.displayName))
            nodes = semanticsNodes(findViewRoot(dialog.window?.decorView))
            assertEquals(color, selectedColors.last())
            assertEquals(color, selectedColor.value)
            assertEquals(
                true,
                nodeWithSelectedDescription(nodes, color.displayName)
                    .config.getOrNull(SemanticsProperties.Selected),
            )
        }
        assertEquals(HighlightColor.entries.toList(), selectedColors)
    }

    @Test
    fun productionSearchResultsDialogScrollsAllThirtyTwoLabeledTermsIncludingZeroCounts() {
        val terms = List(32) { index ->
            SearchTermSummary(
                termId = "term-$index",
                label = "long search phrase label $index",
                color = com.artkachenko.livetextfinder.search.SearchHighlightPalette.colors[index],
                occurrenceCount = if (index == 31) 0 else index + 1,
            )
        }
        mount(widthDp = 320, heightDp = 620, fontScale = 1.2f) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                SearchTermResultsDialog(terms = terms, onDismiss = ::ignoreAction)
            }
        }
        settleDialog()

        val dialog = checkNotNull(ShadowDialog.getLatestDialog())
        val dialogRoot = findViewRoot(dialog.window?.decorView)
        var nodes = semanticsNodes(dialogRoot)
        assertTrue(nodes.any { it.textValues().contains("Search results") })
        assertTrue(nodes.any { it.textValues().contains("long search phrase label 0") })
        assertDialogRendered(dialog, "search-results-32-top-light-large-text.png")
        val scrollNode = checkNotNull(
            nodes.firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null },
        )
        val scrollBy = checkNotNull(scrollNode.config.getOrNull(SemanticsActions.ScrollBy)?.action)
        assertTrue(scrollBy(0f, 2_000f))
        measureCurrentComposition()
        nodes = semanticsNodes(dialogRoot)
        assertTrue(nodes.any { it.textValues().contains("long search phrase label 31") })
        assertTrue(nodes.any { it.textValues().contains("0") })
        assertTouchBounds(nodeWithText(nodes, "Done"), dialog.window?.decorView)
        assertDialogRendered(dialog, "search-results-32-bottom-light-large-text.png")
    }

    @Test
    fun sharedSearchContextKeepsControllerAndUnavailableTermDisclosureInSync() {
        val session = SearchSessionController(InMemorySearchSessionStore())
        val listName = "A very long saved search list name for narrow screens"
        session.saveList(null, listName, "bolt\nnut")
        val listId = checkNotNull(session.state.value.persisted.lists.singleOrNull()).id
        session.setManualQuery("preserved query")
        session.selectList(listId)
        val darkTheme = mutableStateOf(false)
        val saving = mutableStateOf(false)
        val termsOpen = mutableStateOf(false)
        val matchesOpen = mutableStateOf(true)
        var invalidationCount = 0
        var invalidatedBeforeMutation = false
        val queryChange: (String) -> Unit = { text -> session.setManualQuery(text) }

        mount(widthDp = 320, heightDp = 1_000, fontScale = 1.2f) {
            LiveTextFinderTheme(darkThemeOverride = darkTheme.value, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column {
                        FinderSearchControls(
                            showTitle = false,
                            query = session.state.value.persisted.manualQuery,
                            onQueryChange = queryChange,
                            searchListsLabel = "Lists",
                            onSearchLists = ::ignoreAction,
                            activeListName = session.state.value.activeList?.name,
                            activeListTermCount = 2,
                            matchMode = session.state.value.persisted.matchMode,
                            onTerms = {
                                RetainedInspectionActionDispatcher.showTerms(
                                    { termsOpen.value = it },
                                    { matchesOpen.value = it },
                                )
                            },
                            onUseManual = {
                                dispatchSearchListSelection(session, null) {
                                    invalidatedBeforeMutation =
                                        session.state.value.persisted.activeListId == listId
                                    invalidationCount++
                                }
                            },
                            onOptions = ::ignoreAction,
                            saving = saving.value,
                            captureLabel = null,
                            captureEnabled = false,
                            onCapture = ::ignoreAction,
                            pendingCaptureLabel = null,
                            onCancelCapture = ::ignoreAction,
                            secondaryActionLabel = null,
                            onSecondaryAction = ::ignoreAction,
                        )
                        RetainedInspectionHeader(
                            activeListName = session.state.value.activeList?.name,
                            activeListTermCount = 2,
                            manualQuery = session.state.value.persisted.manualQuery,
                            matchMode = session.state.value.persisted.matchMode,
                            saving = saving.value,
                            editing = false,
                            managerOpen = false,
                            returnLabel = null,
                            resultCount = null,
                            occurrenceCount = 0,
                            sharePreparing = false,
                            onEditSearch = ::ignoreAction,
                            onReturn = ::ignoreAction,
                            onTerms = {
                                RetainedInspectionActionDispatcher.showTerms(
                                    { termsOpen.value = it },
                                    { matchesOpen.value = it },
                                )
                            },
                            onUseManual = {
                                dispatchSearchListSelection(session, null) {
                                    invalidatedBeforeMutation =
                                        session.state.value.persisted.activeListId == listId
                                    invalidationCount++
                                }
                            },
                            onOccurrences = ::ignoreAction,
                            onLists = ::ignoreAction,
                            onOptions = ::ignoreAction,
                            onShare = ::ignoreAction,
                            onCancelShare = ::ignoreAction,
                        )
                        if (termsOpen.value) {
                            SearchTermResultsDialog(
                                terms = listOf(
                                    SearchTermSummary(
                                        "bolt",
                                        "bolt",
                                        com.artkachenko.livetextfinder.search.SearchHighlightPalette
                                            .colors.first(),
                                        3,
                                    ),
                                ),
                                resultsAvailable = false,
                                canLocate = false,
                                onDismiss = { termsOpen.value = false },
                            )
                        }
                    }
                }
            }
        }

        var nodes = semanticsNodes(composeView)
        assertTrue(nodes.count { it.textValues().contains("Substring") } >= 2)
        assertTrue(nodes.count { it.textValues().contains("2 terms") } >= 2)
        assertTrue(nodes.count { it.textValues().contains("Use manual search") } >= 2)
        assertTrue(nodes.any { it.textValues().contains("Results") })
        assertFalse(nodes.any { it.textValues().contains("Results (0)") })
        fun assertActionSize(label: String) {
            val density = composeView!!.resources.displayMetrics.density
            val actions = nodes.filter { node ->
                node.textValues().contains(label) &&
                    node.config.getOrNull(SemanticsActions.OnClick) != null
            }
            assertEquals(2, actions.size, "$label action count")
            actions.forEach { node ->
                assertTrue(node.size.width / density >= 48f, "$label width: ${node.size}")
                assertTrue(node.size.height / density >= 48f, "$label height: ${node.size}")
            }
        }
        assertActionSize("Terms")
        assertActionSize("Use manual search")
        assertRendered("search-context-list-light-large-text.png")
        val contextBeforeSave = nodes.first {
            it.textValues().any { text -> text.contains(listName) }
        }.positionInRoot

        saving.value = true
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(nodes.any { it.textValues().contains("Saving…") })
        assertEquals(
            contextBeforeSave,
            nodes.first { it.textValues().any { text -> text.contains(listName) } }.positionInRoot,
        )
        assertRendered("search-context-saving-light-large-text.png")
        saving.value = false
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(nodes.count { it.textValues().contains("Substring") } >= 2)
        assertRendered("search-context-idle-light-large-text.png")

        val identityBeforeModeChange = session.state.value.activeSearch.identity
        session.setMatchMode(SearchMatchMode.WholeWord)
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(nodes.count { it.textValues().contains("Whole words") } >= 2)
        assertTrue(identityBeforeModeChange != session.state.value.activeSearch.identity)
        darkTheme.value = true
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(nodes.count { it.textValues().contains("Whole words") } >= 2)
        assertRendered("search-context-list-dark-large-text.png")

        click(nodeWithText(nodes, "Use manual search", clickable = true))
        assertTrue(invalidatedBeforeMutation)
        assertEquals(1, invalidationCount)
        assertNull(session.state.value.persisted.activeListId)
        assertEquals("preserved query", session.state.value.persisted.manualQuery)
        assertFalse(dispatchSearchListSelection(session, null) { invalidationCount++ })
        assertEquals(1, invalidationCount)

        session.selectList(listId)
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        val queryField = nodes.single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == "preserved query"
        }
        val setQuery = checkNotNull(
            queryField.config.getOrNull(SemanticsActions.SetText)?.action,
        )
        assertTrue(setQuery(AnnotatedString("typed query")))
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        assertNull(session.state.value.persisted.activeListId)
        assertEquals("typed query", session.state.value.persisted.manualQuery)

        nodes = semanticsNodes(composeView)
        click(nodeWithText(nodes, "Terms", clickable = true))
        settleDialog()
        assertFalse(matchesOpen.value)
        assertTrue(termsOpen.value)
        val dialog = checkNotNull(ShadowDialog.getLatestDialog())
        val dialogNodes = semanticsNodes(findViewRoot(dialog.window?.decorView))
        assertTrue(dialogNodes.any {
            it.textValues().contains("Results are not current for this search and image.")
        })
        assertTrue(dialogNodes.any { it.textValues().contains("—") })
        assertTrue(dialogNodes.none { it.textValues().contains("3") })
        assertDialogRendered(dialog, "search-context-terms-unavailable.png")
        assertTouchBounds(nodeWithText(dialogNodes, "Done"), dialog.window?.decorView)
    }

    @Test
    fun productionHighlightPaletteKeepsRowsDisabledUntilSessionIsReady() {
        val selectedColor = mutableStateOf(HighlightColor.Yellow)
        val selectedColors = mutableListOf<HighlightColor>()
        var dismissCount = 0
        mount(widthDp = 360, heightDp = 640) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                HighlightColorDialog(
                    selectedColor = selectedColor.value,
                    enabled = false,
                    onSelectColor = { color ->
                        selectedColors += color
                        selectedColor.value = color
                    },
                    onDismissRequest = { dismissCount++ },
                )
            }
        }
        settleDialog()

        val dialog = checkNotNull(ShadowDialog.getLatestDialog())
        val nodes = semanticsNodes(findViewRoot(dialog.window?.decorView))
        assertDialogRendered(dialog, "highlight-palette-light-ready-gated.png")
        val yellow = nodeWithDescription(nodes, "Yellow")
        assertTrue(yellow.config.contains(SemanticsProperties.Disabled))
        assertTouchBounds(yellow, dialog.window?.decorView)
        dispatchTouch(findComposeView(dialog.window?.decorView), yellow.touchBoundsInRoot)
        measureCurrentComposition()
        assertTrue(selectedColors.isEmpty())
        assertEquals(HighlightColor.Yellow, selectedColor.value)
        val customAction = nodeWithDescription(nodes, "Custom color")
        assertTrue(customAction.config.contains(SemanticsProperties.Disabled))
        assertTouchBounds(customAction, dialog.window?.decorView)
        dispatchTouch(findComposeView(dialog.window?.decorView), customAction.touchBoundsInRoot)
        measureCurrentComposition()
        assertFalse(
            semanticsNodes(findViewRoot(dialog.window?.decorView))
                .any { it.textValues().contains("Apply") },
        )
        val done = nodeWithText(nodes, "Done")
        assertTouchBounds(done, dialog.window?.decorView)
        click(done)
        assertEquals(1, dismissCount)
    }

    @Test
    fun shortHighlightDialogScrollsPaletteAndKeepsDoneVisibleAndTouchable() {
        val selectedColor = mutableStateOf(HighlightColor.Yellow)
        val selectedColors = mutableListOf<HighlightColor>()
        var dismissCount = 0
        mount(widthDp = 360, heightDp = 240) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                HighlightColorDialog(
                    selectedColor = selectedColor.value,
                    enabled = true,
                    onSelectColor = { color ->
                        selectedColors += color
                        selectedColor.value = color
                    },
                    onDismissRequest = { dismissCount++ },
                )
            }
        }
        settleDialog()

        val dialog = checkNotNull(ShadowDialog.getLatestDialog())
        val decorView = checkNotNull(dialog.window?.decorView)
        val density = checkNotNull(activity).resources.displayMetrics.density
        val widthPx = (360 * density).roundToInt()
        val heightPx = (240 * density).roundToInt()
        dialog.window?.setLayout(widthPx, heightPx)
        shadowOf(Looper.getMainLooper()).idle()
        findViewRoot(decorView).measureAndLayoutForTest()
        measureCurrentComposition()
        assertEquals(heightPx, decorView.height)

        var nodes = semanticsNodes(findViewRoot(decorView))
        val scrollNode = checkNotNull(
            nodes.firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null },
        )
        checkNotNull(scrollNode.config.getOrNull(SemanticsActions.ScrollBy)?.action)
            .invoke(0f, 320f)
        measureCurrentComposition()
        nodes = semanticsNodes(findViewRoot(decorView))

        var viewport = scrollViewportNode(nodes)
        scrollNodeIntoViewport(findViewRoot(decorView), "White")
        nodes = semanticsNodes(findViewRoot(decorView))
        viewport = scrollViewportNode(nodes)
        val white = nodeWithDescription(nodes, "White")
        assertFullyVisibleInViewport(white, viewport)
        assertTouchBounds(white, decorView)
        val done = nodeWithText(nodes, "Done")
        assertTouchBounds(done, decorView)
        assertTrue(done.positionInRoot.y >= 0f)
        assertTrue(done.positionInRoot.y + done.size.height <= decorView.height)
        click(white)
        assertEquals(listOf(HighlightColor.White), selectedColors)
        click(nodeWithText(semanticsNodes(findViewRoot(decorView)), "Done"))
        assertEquals(1, dismissCount)
    }

    @Test
    fun productionCustomColorUsesAnUncommittedRgbDraftAndAppliesOnce() {
        val selectedColor = mutableStateOf(HighlightColor.Yellow)
        val selectedColors = mutableListOf<HighlightColor>()
        val isOpen = mutableStateOf(true)
        val enabled = mutableStateOf(true)
        var dismissCount = 0
        mount(widthDp = 360, heightDp = 320, fontScale = 1.3f) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                if (isOpen.value) {
                    HighlightColorDialog(
                        selectedColor = selectedColor.value,
                        enabled = enabled.value,
                        onSelectColor = { color ->
                            selectedColors += color
                            selectedColor.value = color
                        },
                        onDismissRequest = {
                            dismissCount++
                            isOpen.value = false
                        },
                    )
                }
            }
        }
        settleDialog()

        var dialog = checkNotNull(ShadowDialog.getLatestDialog())
        var decorView = checkNotNull(dialog.window?.decorView)
        val density = checkNotNull(activity).resources.displayMetrics.density
        dialog.window?.setLayout(
            (360 * density).roundToInt(),
            (320 * density).roundToInt(),
        )
        shadowOf(Looper.getMainLooper()).idle()
        findViewRoot(decorView).measureAndLayoutForTest()
        measureCurrentComposition()
        assertEquals((320 * density).roundToInt(), decorView.height)
        click(nodeWithDescription(semanticsNodes(findViewRoot(decorView)), "Custom color"))
        var nodes = semanticsNodes(findViewRoot(decorView))
        assertTrue(nodes.any { it.textValues().contains("#FFFF00") })
        assertDialogRendered(
            dialog,
            "custom-highlight-editor-light-large-text.png",
        )
        val apply = nodeWithText(nodes, "Apply")
        val cancel = nodeWithText(nodes, "Cancel")
        assertFullyVisibleInDialog(apply, decorView)
        assertFullyVisibleInDialog(cancel, decorView)
        assertTouchBounds(apply, decorView)
        assertTouchBounds(cancel, decorView)

        val dialogRoot = findViewRoot(decorView)
        for ((label, channel) in listOf("Red" to 0, "Green" to 1, "Blue" to 2)) {
            scrollNodeIntoViewport(dialogRoot, label)
            nodes = semanticsNodes(dialogRoot)
            val viewport = scrollViewportNode(nodes)
            val slider = nodeWithDescription(nodes, label)
            assertFullyVisibleInViewport(slider, viewport)
            assertTouchBounds(slider, decorView)
            assertSliderLayoutHeight(slider, decorView)

            var beforeTouch = previewColor(nodes)
            dispatchTouchAt(
                findComposeView(decorView),
                slider.touchBoundsInRoot.left + slider.touchBoundsInRoot.width * 0.25f,
                slider.touchBoundsInRoot.top + 1f,
            )
            measureCurrentComposition()
            nodes = semanticsNodes(dialogRoot)
            var afterTouch = previewColor(nodes)
            assertChannelChangedOnly(beforeTouch, afterTouch, channel)
            assertEquals(HighlightColor.Yellow, selectedColor.value)
            assertTrue(selectedColors.isEmpty())

            beforeTouch = afterTouch
            dispatchTouchAt(
                findComposeView(decorView),
                slider.touchBoundsInRoot.left + slider.touchBoundsInRoot.width * 0.75f,
                slider.touchBoundsInRoot.bottom - 1f,
            )
            measureCurrentComposition()
            nodes = semanticsNodes(dialogRoot)
            afterTouch = previewColor(nodes)
            assertChannelChangedOnly(beforeTouch, afterTouch, channel)
            assertEquals(HighlightColor.Yellow, selectedColor.value)
            assertTrue(selectedColors.isEmpty())
        }

        enabled.value = false
        measureCurrentComposition()
        nodes = semanticsNodes(findViewRoot(decorView))
        val disabledApply = nodeWithText(nodes, "Apply")
        assertTrue(disabledApply.config.contains(SemanticsProperties.Disabled))
        dispatchTouch(findComposeView(decorView), disabledApply.touchBoundsInRoot)
        measureCurrentComposition()
        assertEquals(HighlightColor.Yellow, selectedColor.value)
        assertTrue(selectedColors.isEmpty())
        enabled.value = true
        measureCurrentComposition()
        nodes = semanticsNodes(findViewRoot(decorView))

        click(nodeWithText(nodes, "Cancel"))
        nodes = semanticsNodes(findViewRoot(decorView))
        click(nodeWithDescription(nodes, "Custom color"))
        nodes = semanticsNodes(findViewRoot(decorView))
        assertTrue(nodes.any { it.textValues().contains("#FFFF00") })
        scrollNodeIntoViewport(findViewRoot(decorView), "Red")
        nodes = semanticsNodes(findViewRoot(decorView))
        val reopenedViewport = scrollViewportNode(nodes)
        val reopenedRedSlider = nodeWithDescription(nodes, "Red")
        assertFullyVisibleInViewport(reopenedRedSlider, reopenedViewport)
        assertTouchBounds(reopenedRedSlider, decorView)
        dispatchTouchAt(
            findComposeView(decorView),
            reopenedRedSlider.positionInRoot.x + reopenedRedSlider.size.width * 0.05f,
            reopenedRedSlider.touchBoundsInRoot.center.y,
        )
        measureCurrentComposition()
        nodes = semanticsNodes(findViewRoot(decorView))
        assertTrue(nodes.any { it.textValues().contains("#01FF00") })

        click(nodeWithText(nodes, "Apply"))
        assertEquals(listOf(HighlightColor.fromRgb(1, 255, 0)), selectedColors)
        assertEquals(HighlightColor.fromRgb(1, 255, 0), selectedColor.value)
        assertEquals(1, dismissCount)

        isOpen.value = true
        measureCurrentComposition()
        dialog = checkNotNull(ShadowDialog.getLatestDialog())
        decorView = checkNotNull(dialog.window?.decorView)
        nodes = semanticsNodes(findViewRoot(decorView))
        val selectedCustomRow = nodeWithDescription(nodes, "Custom color (#01FF00)")
        assertEquals(true, selectedCustomRow.config.getOrNull(SemanticsProperties.Selected))
        click(selectedCustomRow)
        nodes = semanticsNodes(findViewRoot(decorView))
        assertTrue(nodes.any { it.textValues().contains("#01FF00") })
        click(nodeWithText(nodes, "Cancel"))
        nodes = semanticsNodes(findViewRoot(decorView))
        assertTrue(nodes.any { it.textValues().contains("#01FF00") })
        assertEquals(1, selectedColors.size)
        assertEquals(1, dismissCount)
    }

    @Test
    fun productionCustomEditorRendersSelectedColorInDarkThemeAtLargeText() {
        val selectedColor = HighlightColor.fromRgb(18, 171, 239)
        mount(widthDp = 360, heightDp = 640, fontScale = 1.3f) {
            LiveTextFinderTheme(darkThemeOverride = true, dynamicColor = false) {
                HighlightColorDialog(
                    selectedColor = selectedColor,
                    enabled = true,
                    onSelectColor = { ignoreAction() },
                    onDismissRequest = ::ignoreAction,
                )
            }
        }
        settleDialog()

        val dialog = checkNotNull(ShadowDialog.getLatestDialog())
        val decorView = checkNotNull(dialog.window?.decorView)
        val nodes = semanticsNodes(findViewRoot(decorView))
        val customRow = nodeWithDescription(nodes, "Custom color (#12ABEF)")
        assertEquals(true, customRow.config.getOrNull(SemanticsProperties.Selected))
        click(customRow)

        val editorNodes = semanticsNodes(findViewRoot(decorView))
        assertTrue(editorNodes.any { it.textValues().contains("#12ABEF") })
        assertDialogRendered(dialog, "custom-highlight-editor-dark-large-text.png")
    }

    @Test
    fun productionCustomEditorShowsThreeChannelsAndFixedActionsInLargeViewport() {
        mount(widthDp = 360, heightDp = 700, fontScale = 1.3f) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                HighlightColorDialog(
                    selectedColor = HighlightColor.fromRgb(18, 171, 239),
                    enabled = true,
                    onSelectColor = { ignoreAction() },
                    onDismissRequest = ::ignoreAction,
                )
            }
        }
        settleDialog()
        val dialog = checkNotNull(ShadowDialog.getLatestDialog())
        val decorView = checkNotNull(dialog.window?.decorView)
        val customColorRow = nodeWithDescription(
            semanticsNodes(findViewRoot(decorView)),
            "Custom color (#12ABEF)",
        )
        click(customColorRow)
        val nodes = semanticsNodes(findViewRoot(decorView))
        listOf("Red", "Green", "Blue").forEach { label ->
            val slider = nodeWithDescription(nodes, label)
            assertTouchBounds(slider, decorView)
            assertSliderLayoutHeight(slider, decorView)
            assertFullyVisibleInDialog(slider, decorView)
        }
        assertFullyVisibleInDialog(nodeWithText(nodes, "Apply"), decorView)
        assertFullyVisibleInDialog(nodeWithText(nodes, "Cancel"), decorView)
        assertTouchBounds(nodeWithText(nodes, "Apply"), decorView)
        assertTouchBounds(nodeWithText(nodes, "Cancel"), decorView)
        assertDialogRendered(dialog, "custom-highlight-editor-light-all-channels-large-text.png")
    }

    @Test
    fun systemBackDismissesCustomDraftWithoutApplyingIt() {
        val selectedColor = mutableStateOf(HighlightColor.Yellow)
        val selectedColors = mutableListOf<HighlightColor>()
        val isOpen = mutableStateOf(true)
        var dismissCount = 0
        mount(widthDp = 360, heightDp = 640) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                if (isOpen.value) {
                    HighlightColorDialog(
                        selectedColor = selectedColor.value,
                        enabled = true,
                        onSelectColor = { color ->
                            selectedColors += color
                            selectedColor.value = color
                        },
                        onDismissRequest = {
                            dismissCount++
                            isOpen.value = false
                        },
                    )
                }
            }
        }
        settleDialog()

        val dialog = checkNotNull(ShadowDialog.getLatestDialog())
        val decorView = checkNotNull(dialog.window?.decorView)
        click(nodeWithDescription(semanticsNodes(findViewRoot(decorView)), "Custom color"))
        var nodes = semanticsNodes(findViewRoot(decorView))
        scrollNodeIntoViewport(findViewRoot(decorView), "Red")
        nodes = semanticsNodes(findViewRoot(decorView))
        val viewport = scrollViewportNode(nodes)
        val redSlider = nodeWithDescription(nodes, "Red")
        assertFullyVisibleInViewport(redSlider, viewport)
        assertTouchBounds(redSlider, decorView)
        dispatchTouchAt(
            findComposeView(decorView),
            redSlider.positionInRoot.x + redSlider.size.width * 0.05f,
            redSlider.touchBoundsInRoot.center.y,
        )
        measureCurrentComposition()
        nodes = semanticsNodes(findViewRoot(decorView))
        assertTrue(nodes.any { it.textValues().contains("#00FF00") })
        assertEquals(HighlightColor.Yellow, selectedColor.value)

        checkNotNull(dialog as? ComponentDialog).onBackPressedDispatcher.onBackPressed()
        measureCurrentComposition()

        assertEquals(0, selectedColors.size)
        assertEquals(HighlightColor.Yellow, selectedColor.value)
        assertEquals(1, dismissCount)
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
                it.textValues().contains("Allow camera access to scan text")
            },
        )
        assertTrue(nodes.any { it.textValues().contains("Image import failed.") })

        state.value = OcrUiState(OcrStatus.Starting)
        measureCurrentComposition()
        nodes = semanticsNodes(composeView)
        assertTrue(nodes.any { it.textValues().contains("Starting camera…") })
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
        assertTrue(nodes.any { it.textValues().contains("Camera unavailable") })
        assertFalse(
            nodes.any {
                it.textValues().contains("No rear camera is available on this device.")
            },
        )
        assertRendered("status-camera-unavailable-light.png")
        click(nodeWithText(nodes, "Details"))
        settleDialog()
        val unavailableDialog = checkNotNull(ShadowDialog.getLatestDialog())
        val unavailableDialogNodes = semanticsNodes(
            findViewRoot(unavailableDialog.window?.decorView),
        )
        assertTrue(
            unavailableDialogNodes.any {
                it.textValues().contains("No rear camera is available on this device.")
            },
        )
        click(nodeWithText(unavailableDialogNodes, "Close"))

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
                it.textValues().contains("Imported image · No matches")
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
        assertFalse(nodes.any { it.textValues().contains("Saving…") })

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
    fun productionSelectionSerializesRealWritesAndKeepsMeasuredBoundsStable() {
        runSelectionComposition(320, 1.4f, darkTheme = true)
        disposeComposition()
        runSelectionComposition(360, 1.25f, darkTheme = false)
    }

    private fun runSelectionComposition(widthDp: Int, fontScale: Float, darkTheme: Boolean) {
        val listA = SavedSearchList(
            "a",
            "Invoices and receipts for the monthly review 001",
            listOf("invoice"),
        )
        val listB = SavedSearchList(
            "b",
            "Invoices and receipts for the monthly review 002",
            listOf("receipt"),
        )
        val store = ControlledSearchSessionStore()
        val session = SearchSessionController(store)
        var revision = 0
        var expectedActiveId: String? = null
        fun dispatch(id: String?): Boolean = dispatchSearchListSelection(session, id) {
            assertEquals(expectedActiveId, session.state.value.persisted.activeListId)
            revision++
        }
        assertFalse(dispatch(listA.id))
        assertFalse(dispatch(null))
        assertEquals(0, revision)
        assertTrue(store.writes.isEmpty())
        store.completeLoad(PersistedSearchState("manual", listOf(listA, listB), listA.id))
        assertFalse(dispatch("missing"))
        assertEquals(0, store.writes.size)

        val managerOpen = mutableStateOf(true)
        val scroll = ScrollState(0)
        contentScrollState = scroll
        mount(widthDp, 900, fontScale) {
            LiveTextFinderTheme(darkThemeOverride = darkTheme, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    AppContentColumn(
                        scrollState = scroll,
                        scrollable = false,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Box(Modifier.semantics { contentDescription = "Controls bounds" }) {
                                FinderSearchControls(
                                    showTitle = false,
                                    query = session.state.value.persisted.manualQuery,
                                    onQueryChange = session::setManualQuery,
                                    searchListsLabel = "Search lists",
                                    onSearchLists = { managerOpen.value = !managerOpen.value },
                                    searchContext = session.state.value.activeList?.name
                                        ?: "Manual search",
                                    onOptions = ::ignoreAction,
                                    savingLabel = if (session.state.value.saving) {
                                        "Saving…"
                                    } else {
                                        null
                                    },
                                    captureLabel = null,
                                    captureEnabled = false,
                                    onCapture = ::ignoreAction,
                                    pendingCaptureLabel = null,
                                    onCancelCapture = ::ignoreAction,
                                    secondaryActionLabel = null,
                                    onSecondaryAction = ::ignoreAction,
                                )
                            }
                            TestFinderStatusRegion(
                                state = OcrUiState(OcrStatus.Paused),
                                importedState = ImportedImageState(),
                                sessionState = session.state.value,
                                shouldShowViews = true,
                                onRetry = session::retry,
                            )
                        }
                        if (managerOpen.value) {
                            Box(Modifier.semantics { contentDescription = "List bounds" }) {
                                FinderSearchLists(
                                    lists = session.state.value.persisted.lists,
                                    activeListId = session.state.value.persisted.activeListId,
                                    editor = SearchListEditorState(),
                                    canSave = session.state.value.loaded,
                                    onNew = ::ignoreAction,
                                    onSelect = { id -> dispatch(id) },
                                    onEdit = ::ignoreList,
                                    onDelete = ::ignoreList,
                                    onNameChange = ::ignoreText,
                                    onTermsChange = ::ignoreText,
                                    onSave = ::ignoreAction,
                                    onCancel = ::ignoreAction,
                                )
                            }
                        } else {
                            Box(
                                Modifier.weight(1f).fillMaxWidth().background(Color.Black)
                                    .semantics { contentDescription = "Preview region" },
                            )
                        }
                    }
                }
            }
        }

        val idleNodes = semanticsNodes(composeView)
        val idleState = session.state.value
        val idleControlsBounds = layoutBounds(nodeWithDescription(idleNodes, "Controls bounds"))
        expectedActiveId = listA.id
        click(nodeWithDescription(idleNodes, "Select ${listA.name}"))
        assertSame(idleState, session.state.value)
        assertEquals(0, revision)
        assertTrue(store.writes.isEmpty())

        expectedActiveId = listA.id
        click(nodeWithDescription(semanticsNodes(composeView), "Select ${listB.name}"))
        assertEquals(1, revision)
        assertEquals(listB.id, session.state.value.persisted.activeListId)
        assertEquals(1, store.writes.size)
        assertTrue(session.state.value.saving)
        var savingNodes = semanticsNodes(composeView)
        assertTrue(savingNodes.any { it.textValues().contains("Saving…") })
        assertEquals(
            idleControlsBounds,
            layoutBounds(nodeWithDescription(savingNodes, "Controls bounds")),
        )
        val pendingBounds = selectionBounds(savingNodes, listA, listB)
        assertRendered("selection-${widthDp}-manager-saving.png")
        val pendingState = session.state.value
        expectedActiveId = listB.id
        click(nodeWithDescription(savingNodes, "Select ${listB.name}"))
        assertSame(pendingState, session.state.value)
        assertEquals(1, revision)
        assertEquals(1, store.writes.size)
        store.completeWrite(0)
        measureCurrentComposition()
        assertFalse(session.state.value.saving)
        assertEquals(1, store.writes.size)
        assertEquals(pendingBounds, selectionBounds(semanticsNodes(composeView), listA, listB))
        assertRendered("selection-${widthDp}-saving.png")

        expectedActiveId = listB.id
        click(nodeWithDescription(semanticsNodes(composeView), "Select ${listA.name}"))
        expectedActiveId = listA.id
        click(nodeWithDescription(semanticsNodes(composeView), "Select ${listB.name}"))
        assertEquals(3, revision)
        assertEquals(2, store.writes.size)
        expectedActiveId = listB.id
        click(nodeWithDescription(semanticsNodes(composeView), "Select ${listB.name}"))
        assertEquals(3, revision)
        assertEquals(2, store.writes.size)
        store.completeWrite(1)
        assertEquals(3, store.writes.size)
        assertEquals(listB.id, store.writes[2].first.activeListId)
        store.completeWrite(2)
        assertFalse(session.state.value.saving)

        expectedActiveId = listB.id
        click(nodeWithDescription(semanticsNodes(composeView), "Select ${listA.name}"))
        store.completeWrite(3)
        expectedActiveId = listA.id
        click(nodeWithDescription(semanticsNodes(composeView), "Select ${listB.name}"))
        store.completeWrite(4, Result.failure(IllegalStateException("disk full")))
        measureCurrentComposition()
        val failedState = session.state.value
        assertEquals(listB.id, failedState.persisted.activeListId)
        assertEquals("disk full", failedState.error)
        expectedActiveId = listB.id
        click(nodeWithDescription(semanticsNodes(composeView), "Select ${listB.name}"))
        assertSame(failedState, session.state.value)
        assertEquals(5, revision)
        assertEquals(5, store.writes.size)
        click(nodeWithText(semanticsNodes(composeView), "Retry"))
        assertEquals(6, store.writes.size)
        store.completeWrite(5)
        measureCurrentComposition()
        assertNull(session.state.value.error)

        assertFalse(dispatch("missing"))
        assertEquals(5, revision)
        assertEquals(6, store.writes.size)
        expectedActiveId = listB.id
        assertTrue(dispatch(null))
        assertEquals(6, revision)
        assertEquals(null, session.state.value.persisted.activeListId)
        assertEquals(7, store.writes.size)
        val manualPending = session.state.value
        expectedActiveId = null
        assertFalse(dispatch(null))
        assertSame(manualPending, session.state.value)
        assertEquals(6, revision)
        store.completeWrite(6)

        managerOpen.value = false
        measureCurrentComposition()
        val previewIdle = semanticsNodes(composeView)
        val previewIdleBounds = layoutBounds(nodeWithDescription(previewIdle, "Preview region"))
        assertTrue(previewIdleBounds.second.height > 0)
        assertRendered("selection-${widthDp}-preview-idle.png")
        expectedActiveId = null
        assertTrue(dispatch(listB.id))
        measureCurrentComposition()
        savingNodes = semanticsNodes(composeView)
        assertTrue(savingNodes.any { it.textValues().contains("Saving…") })
        assertRendered("selection-${widthDp}-preview-saving.png")
        val previewSavingBounds = layoutBounds(
            nodeWithDescription(savingNodes, "Preview region"),
        )
        val controlsSavingBounds = layoutBounds(
            nodeWithDescription(savingNodes, "Controls bounds"),
        )
        assertTrue(previewSavingBounds.second.height > 0)
        store.completeWrite(7)
        measureCurrentComposition()
        val completedNodes = semanticsNodes(composeView)
        assertFalse(session.state.value.saving)
        assertEquals(
            previewSavingBounds,
            layoutBounds(nodeWithDescription(completedNodes, "Preview region")),
        )
        assertEquals(
            controlsSavingBounds,
            layoutBounds(nodeWithDescription(completedNodes, "Controls bounds")),
        )
    }

    private fun layoutBounds(node: SemanticsNode) = node.positionInRoot to node.size

    private fun selectionBounds(
        nodes: List<SemanticsNode>,
        listA: SavedSearchList,
        listB: SavedSearchList,
    ): List<Pair<androidx.compose.ui.geometry.Offset, androidx.compose.ui.unit.IntSize>> =
        listOf(
            nodeWithDescription(nodes, "Controls bounds"),
            nodeWithDescription(nodes, "List bounds"),
            nodeWithDescription(nodes, "Select ${listA.name}"),
            nodeWithDescription(nodes, "Select ${listB.name}"),
        ).map { node -> node.positionInRoot to node.size }

    @Test
    fun compactControlsStatusAndWeightedPreviewKeepRecoveryActionReachable() {
        var resetCount = 0
        val saving = mutableStateOf(false)
        mount(widthDp = 360, heightDp = 700, fontScale = 1.25f) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    AppContentColumn(
                        scrollState = ScrollState(0),
                        scrollable = false,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(
                                horizontal = FinderSpacing.large,
                                vertical = FinderSpacing.small,
                            ),
                            verticalArrangement = Arrangement.spacedBy(FinderSpacing.small),
                        ) {
                            FinderSearchControls(
                                showTitle = true,
                                query = "invoice",
                                onQueryChange = ::ignoreText,
                                searchListsLabel = "Search lists",
                                onSearchLists = ::ignoreAction,
                                searchContext = "Manual search",
                                onOptions = ::ignoreAction,
                                savingLabel = if (saving.value) "Saving…" else null,
                                captureLabel = "Freeze frame",
                                captureEnabled = true,
                                onCapture = ::ignoreAction,
                                pendingCaptureLabel = null,
                                onCancelCapture = ::ignoreAction,
                                secondaryActionLabel = null,
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
        val statusText = nodeWithText(nodes, "Scanning · 2 matches")
        assertTrue(preview.positionInRoot.y > recoveryText.positionInRoot.y)
        assertTrue(preview.positionInRoot.y > statusText.positionInRoot.y)
        assertWithinViewport(preview)
        assertRendered("status-light-combined-preview.png")
        val recovery = nodeWithText(nodes, "Reset saved data")
        assertTrue(recovery.size.width > 0 && recovery.size.height > 0)
        assertWithinViewport(recovery)
        assertTouchBounds(recovery, composeView)
        assertRendered("status-light-idle-caption.png")
        val previewTop = preview.positionInRoot.y
        saving.value = true
        measureCurrentComposition()
        val savingNodes = semanticsNodes(composeView)
        assertTrue(savingNodes.any { it.textValues().contains("Saving…") })
        assertEquals(
            previewTop,
            nodeWithDescription(savingNodes, "Preview region").positionInRoot.y,
        )
        assertRendered("status-light-saving-caption.png")
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
                            onOptions = ::ignoreAction,
                            captureLabel = null,
                            captureEnabled = false,
                            onCapture = ::ignoreAction,
                            pendingCaptureLabel = null,
                            onCancelCapture = ::ignoreAction,
                            secondaryActionLabel = null,
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
        val testFrameClock = BroadcastFrameClock()
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Unconfined + testFrameClock +
                ImmediateMotionDurationScale,
        )
        val testRecomposer = Recomposer(scope.coroutineContext)
        scope.launch { testRecomposer.runRecomposeAndApplyChanges() }
        hostView.setParentCompositionContext(testRecomposer)
        activity = hostActivity
        composeView = hostView
        frameClock = testFrameClock
        recomposer = testRecomposer
        recomposerScope = scope
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
        measureCurrentComposition()
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

    private fun findComposeView(view: View?): View = when {
        view is ViewRootForTest -> view
        view is ViewGroup -> (0 until view.childCount)
            .asSequence()
            .mapNotNull { index ->
                runCatching { findComposeView(view.getChildAt(index)) }.getOrNull()
            }
            .firstOrNull()
            ?: error("No Compose Android view found")
        else -> error("No Compose Android view found")
    }

    private fun dispatchTouch(view: View, bounds: Rect) {
        val x = bounds.center.x
        val y = bounds.center.y
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0,
        )
        try {
            view.dispatchTouchEvent(down)
        } finally {
            down.recycle()
        }

        val upTime = maxOf(SystemClock.uptimeMillis(), downTime + 1L)
        val up = MotionEvent.obtain(
            downTime,
            upTime,
            MotionEvent.ACTION_UP,
            x,
            y,
            0,
        )
        try {
            view.dispatchTouchEvent(up)
        } finally {
            up.recycle()
        }
    }

    private fun dispatchTouchAt(view: View, x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0,
        )
        try {
            view.dispatchTouchEvent(down)
        } finally {
            down.recycle()
        }

        val up = MotionEvent.obtain(
            downTime,
            downTime + 1L,
            MotionEvent.ACTION_UP,
            x,
            y,
            0,
        )
        try {
            view.dispatchTouchEvent(up)
        } finally {
            up.recycle()
        }
    }

    private fun nodeWithText(
        nodes: List<SemanticsNode>,
        text: String,
        clickable: Boolean = false,
    ): SemanticsNode = checkNotNull(
        nodes.firstOrNull { node ->
            node.textValues().contains(text) &&
                (!clickable || node.config.contains(SemanticsActions.OnClick))
        },
    ) {
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

    private fun nodeWithSelectedDescription(
        nodes: List<SemanticsNode>,
        description: String,
    ): SemanticsNode = checkNotNull(
        nodes.firstOrNull {
            it.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.contains(description) == true &&
                it.config.getOrNull(SemanticsProperties.Selected) == true
        },
    ) {
        "No selected semantics node with description '$description'"
    }

    private fun SemanticsNode.textValues(): List<String> =
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    private fun previewColor(nodes: List<SemanticsNode>): HighlightColor {
        val hex = nodes.asSequence()
            .flatMap { it.textValues().asSequence() }
            .first { value -> value.matches(Regex("#[0-9A-F]{6}")) }
        return HighlightColor.fromRgb(
            hex.substring(1, 3).toInt(16),
            hex.substring(3, 5).toInt(16),
            hex.substring(5, 7).toInt(16),
        )
    }

    private fun assertChannelChangedOnly(
        before: HighlightColor,
        after: HighlightColor,
        channel: Int,
    ) {
        val channelsBefore = listOf(
            before.argb shr 16 and 0xFF,
            before.argb shr 8 and 0xFF,
            before.argb and 0xFF,
        )
        val channelsAfter = listOf(
            after.argb shr 16 and 0xFF,
            after.argb shr 8 and 0xFF,
            after.argb and 0xFF,
        )
        assertTrue(channelsBefore[channel] != channelsAfter[channel])
        channelsBefore.indices.filter { it != channel }.forEach { otherChannel ->
            assertEquals(channelsBefore[otherChannel], channelsAfter[otherChannel])
        }
    }

    private fun assertTouchBounds(node: SemanticsNode, view: View? = composeView) {
        val density = checkNotNull(view).resources.displayMetrics.density
        assertTrue(node.touchBoundsInRoot.width / density >= 48f)
        assertTrue(
            node.touchBoundsInRoot.height / density >= 48f,
            "Touch ${node.touchBoundsInRoot}; layout ${node.positionInRoot} ${node.size}; " +
                "density $density",
        )
    }

    private fun assertSliderLayoutHeight(node: SemanticsNode, view: View?) {
        val density = checkNotNull(view).resources.displayMetrics.density
        assertTrue(
            node.size.height / density >= 48f,
            "Slider layout ${node.size} is shorter than 48dp at density $density",
        )
    }

    private fun Rect.intersects(other: Rect): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

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

    private fun scrollViewportNode(nodes: List<SemanticsNode>): SemanticsNode = checkNotNull(
        nodes.firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null },
    )

    private fun scrollNodeIntoViewport(
        root: ViewRootForTest,
        description: String,
    ): SemanticsNode {
        var nodes = semanticsNodes(root)
        repeat(8) {
            val target = nodeWithDescription(nodes, description)
            val viewport = scrollViewportNode(nodes)
            val viewportTop = viewport.positionInRoot.y
            val viewportBottom = viewportTop + viewport.size.height
            val targetTop = target.positionInRoot.y
            val targetBottom = targetTop + target.size.height
            if (targetTop >= viewportTop && targetBottom <= viewportBottom) {
                return target
            }
            val delta = if (targetBottom > viewportBottom) {
                targetBottom - viewportBottom
            } else {
                targetTop - viewportTop
            }
            checkNotNull(viewport.config.getOrNull(SemanticsActions.ScrollBy)?.action)
                .invoke(0f, delta)
            measureCurrentComposition()
            nodes = semanticsNodes(root)
        }
        error("Could not scroll '$description' fully into the dialog viewport.")
    }

    private fun scrollTextIntoViewport(
        nodes: List<SemanticsNode>,
        text: String,
    ): SemanticsNode {
        var currentNodes = nodes
        repeat(8) {
            val target = nodeWithText(currentNodes, text, clickable = true)
            val viewport = scrollViewportNode(currentNodes)
            val viewportTop = viewport.positionInRoot.y
            val viewportBottom = viewportTop + viewport.size.height
            val targetTop = target.positionInRoot.y
            val targetBottom = targetTop + target.size.height
            if (targetTop >= viewportTop && targetBottom <= viewportBottom) {
                return target
            }
            val delta = if (targetBottom > viewportBottom) {
                targetBottom - viewportBottom
            } else {
                targetTop - viewportTop
            }
            checkNotNull(viewport.config.getOrNull(SemanticsActions.ScrollBy)?.action)
                .invoke(0f, delta)
            measureCurrentComposition()
            currentNodes = semanticsNodes(composeView)
        }
        error("Could not scroll '$text' fully into the editor viewport.")
    }

    private fun assertFullyVisibleInViewport(node: SemanticsNode, viewport: SemanticsNode) {
        val viewportTop = viewport.positionInRoot.y
        val viewportBottom = viewportTop + viewport.size.height
        assertTrue(node.positionInRoot.y >= viewportTop)
        assertTrue(node.positionInRoot.y + node.size.height <= viewportBottom)
    }

    private fun assertFullyVisibleInDialog(node: SemanticsNode, dialogView: View) {
        assertTrue(node.positionInRoot.x >= 0f)
        assertTrue(node.positionInRoot.y >= 0f)
        assertTrue(node.positionInRoot.x + node.size.width <= dialogView.width)
        assertTrue(node.positionInRoot.y + node.size.height <= dialogView.height)
    }

    private fun settleDialog() {
        measureCurrentComposition()
    }

    private fun measureCurrentComposition() {
        Snapshot.sendApplyNotifications()
        val testFrameClock = checkNotNull(frameClock)
        val testRecomposer = checkNotNull(recomposer)
        var frameCount = 0
        do {
            testFrameClock.sendFrame(nextFrameTimeNanos)
            nextFrameTimeNanos += 16_000_000L
            frameCount++
            shadowOf(Looper.getMainLooper()).idle()
            measureHostRoot()
            ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView?.let {
                findViewRoot(it).measureAndLayoutForTest()
            }
            if (frameCount == MAX_RECOMPOSITION_FRAMES &&
                testRecomposer.currentState.value != Recomposer.State.Idle
            ) {
                error(
                    "Compose recomposer remained ${testRecomposer.currentState.value} " +
                        "after $MAX_RECOMPOSITION_FRAMES test frames",
                )
            }
        } while (testRecomposer.currentState.value != Recomposer.State.Idle)
    }

    private fun advanceFramesUntil(predicate: () -> Boolean) {
        repeat(48) {
            if (predicate()) {
                return
            }
            checkNotNull(frameClock).sendFrame(nextFrameTimeNanos)
            nextFrameTimeNanos += 16_000_000L
            shadowOf(Looper.getMainLooper()).idle()
            measureHostRoot()
            ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView?.let {
                findViewRoot(it).measureAndLayoutForTest()
            }
        }
        error("The expected native sheet semantics did not become visible within 48 frames.")
    }

    private fun optionsDialog(): android.app.Dialog = checkNotNull(
        ShadowDialog.getLatestDialog()?.takeIf { it.isShowing },
    )

    private fun optionsDialogNodes(): List<SemanticsNode> =
        semanticsNodes(findViewRoot(optionsDialog().window?.decorView))

    private fun activeOptionsDialogNodes(): List<SemanticsNode> =
        ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView?.let { decor ->
            runCatching { semanticsNodes(findViewRoot(decor)) }.getOrNull()
        } ?: emptyList()

    private fun openOptionsSheet() {
        click(nodeWithText(semanticsNodes(composeView), "Options", clickable = true))
        advanceFramesUntil {
            activeOptionsDialogNodes().any { node ->
                node.textValues().contains("Help") && node.size.width > 0 &&
                    node.size.height > 0
            }
        }
        val dialog = optionsDialog()
        val decor = checkNotNull(dialog.window?.decorView)
        val density = checkNotNull(activity).resources.displayMetrics.density
        dialog.window?.setLayout(
            (320 * density).roundToInt(),
            (360 * density).roundToInt(),
        )
        shadowOf(Looper.getMainLooper()).idle()
        findViewRoot(decor).measureAndLayoutForTest()
        measureCurrentComposition()
        assertEquals((320 * density).roundToInt(), decor.width)
        assertEquals((360 * density).roundToInt(), decor.height)
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

    private class ControlledSearchSessionStore : SearchSessionStore {
        private var loadCallback: ((Result<PersistedSearchState?>) -> Unit)? = null
        val writes = mutableListOf<Pair<PersistedSearchState, (Result<Unit>) -> Unit>>()

        override fun load(callback: (Result<PersistedSearchState?>) -> Unit) {
            loadCallback = callback
        }

        override fun save(value: PersistedSearchState, callback: (Result<Unit>) -> Unit) {
            writes += value to callback
        }

        override fun reset(callback: (Result<Unit>) -> Unit) {
            callback(Result.success(Unit))
        }

        fun completeLoad(value: PersistedSearchState?) {
            checkNotNull(loadCallback).invoke(Result.success(value))
        }

        fun completeWrite(index: Int, result: Result<Unit> = Result.success(Unit)) {
            writes[index].second(result)
        }
    }

    private fun ignoreAction() = Unit

    private fun ignoreMatchMode(value: SearchMatchMode) = Unit

    private fun ignoreList(value: SavedSearchList) = Unit

    private fun ignoreText(value: String) = Unit

}
