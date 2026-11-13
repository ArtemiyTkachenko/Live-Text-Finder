package com.artkachenko.livetextfinder.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.artkachenko.livetextfinder.ScreenshotVariant
import com.artkachenko.livetextfinder.assertDialogScreenshot
import com.artkachenko.livetextfinder.drainMainLooper
import com.artkachenko.livetextfinder.scanarea.LiveSearchAreaDialog
import com.artkachenko.livetextfinder.search.HighlightColor
import com.artkachenko.livetextfinder.search.LiveSearchAreaOwner
import com.artkachenko.livetextfinder.search.NormalizedSearchArea
import com.artkachenko.livetextfinder.search.SearchMatchMode
import com.artkachenko.livetextfinder.search.SearchTermSummary
import com.artkachenko.livetextfinder.search.StillMatchRow
import com.artkachenko.livetextfinder.search.TextOccurrence
import com.artkachenko.livetextfinder.status.RecognitionDetails
import com.artkachenko.livetextfinder.status.StatusSource
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.finder_options_highlight
import livetextfinder.shared.generated.resources.highlight_color_custom
import livetextfinder.shared.generated.resources.highlight_color_yellow
import livetextfinder.shared.generated.resources.scan_area_option_full_frame
import livetextfinder.shared.generated.resources.search_list_delete
import livetextfinder.shared.generated.resources.search_list_delete_message
import livetextfinder.shared.generated.resources.search_list_delete_title
import livetextfinder.shared.generated.resources.search_list_transfer_help
import livetextfinder.shared.generated.resources.status_details_title
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DialogScreenshotTest {
    @After
    fun tearDown() {
        drainMainLooper()
    }

    @Test
    fun stillMatches() {
        assertDialogScreenshot("still-matches-dialog", "selected-match", ScreenshotVariant.LIGHT) {
            StillMatchesDialog(
                rows = listOf(
                    StillMatchRow(
                        index = 0,
                        occurrence = TextOccurrence(0, 0, 7, listOf(0)),
                        matchedText = "Invoice",
                        lineContext = "Invoice 1042",
                        termLabel = "invoice",
                        termColor = HighlightColor.Yellow,
                    ),
                    StillMatchRow(
                        index = 1,
                        occurrence = TextOccurrence(1, 0, 7, listOf(0)),
                        matchedText = "Invoice",
                        lineContext = "Invoice date",
                        termLabel = "invoice",
                        termColor = HighlightColor.Yellow,
                    ),
                ),
                selectedIndex = 0,
                copyMessage = null,
                onSelect = {},
                onCopy = ::ignore,
                onCopyLine = ::ignore,
                onShowOnImage = ::ignore,
                onDismiss = ::ignore,
            )
        }
    }

    @Test
    fun searchTermResults() {
        assertDialogScreenshot(
            "search-term-results-dialog",
            "results-available",
            ScreenshotVariant.LIGHT,
        ) {
            SearchTermResultsDialog(
                terms = listOf(
                    SearchTermSummary("invoice", "invoice", HighlightColor.Yellow, 2),
                    SearchTermSummary("total", "total", HighlightColor.Cyan, 1),
                    SearchTermSummary("tax", "tax", HighlightColor.Magenta, 0),
                ),
                onDismiss = ::ignore,
            )
        }
    }

    @Test
    fun statusDetails() {
        ScreenshotVariant.entries.forEach { variant ->
            assertDialogScreenshot("status-details-dialog", "frozen-frame", variant) {
                StatusDetailsDialog(
                    title = stringResource(Res.string.status_details_title),
                    details = RecognitionDetails(
                        source = StatusSource.FrozenFrame,
                        recognizedLineCount = 3,
                        outlinedElementCount = 2,
                        imageWidth = 1280,
                        imageHeight = 720,
                        rotationDegrees = 0,
                        textSample = "Invoice 1042 · Total €36.00",
                        textUnavailable = false,
                        retainedOutlines = false,
                        sourceMessage = null,
                    ),
                    onDismissRequest = ::ignore,
                )
            }
        }
    }

    @Test
    fun highlightColorSwatches() {
        assertDialogScreenshot("highlight-color-dialog", "swatches", ScreenshotVariant.LIGHT) {
            HighlightColorDialog(HighlightColor.Yellow, true, {}, ::ignore)
        }
    }

    @Test
    fun highlightColorCustomEditor() {
        lateinit var customColorLabel: String
        assertDialogScreenshot(
            subject = "highlight-color-dialog",
            state = "custom-editor",
            variant = ScreenshotVariant.LIGHT,
            onDialogReady = { root ->
                val custom = dialogNodes(root).single { node ->
                    node.config.getOrNull(SemanticsProperties.ContentDescription)
                        ?.contains(customColorLabel) == true
                }
                check(checkNotNull(custom.config.getOrNull(SemanticsActions.OnClick)?.action)())
            },
        ) {
            val label = stringResource(Res.string.highlight_color_custom)
            SideEffect { customColorLabel = label }
            HighlightColorDialog(HighlightColor.Cyan, true, {}, ::ignore)
        }
    }

    @Test
    fun liveSearchArea() {
        val owner = LiveSearchAreaOwner()
        val area = NormalizedSearchArea.CENTERED_DEFAULT
        check(owner.apply(owner.revision, area))
        assertDialogScreenshot("live-search-area-dialog", "custom-area", ScreenshotVariant.LIGHT) {
            LiveSearchAreaDialog(area, owner.revision, owner, onDismiss = ::ignore)
        }
    }

    @Test
    fun usageHelp() {
        assertDialogScreenshot("usage-help-dialog", "open", ScreenshotVariant.LIGHT) {
            UsageHelpDialog(onDismissRequest = ::ignore)
        }
    }

    @Test
    fun confirmDestructive() {
        assertDialogScreenshot(
            "confirm-destructive-dialog",
            "delete-list",
            ScreenshotVariant.LIGHT,
        ) {
            ConfirmDestructiveDialog(
                title = stringResource(Res.string.search_list_delete_title),
                message = stringResource(Res.string.search_list_delete_message, "Invoices"),
                confirmLabel = stringResource(Res.string.search_list_delete),
                onConfirm = ::ignore,
                onDismiss = ::ignore,
            )
        }
    }

    @Test
    fun finderOptions() {
        assertDialogScreenshot(
            subject = "finder-options-sheet",
            state = "live-expanded",
            variant = ScreenshotVariant.LIGHT,
            onDialogReady = { root ->
                val expand = dialogNodes(root).firstNotNullOf { node ->
                    node.config.getOrNull(SemanticsActions.Expand)?.action
                }
                check(expand())
            },
        ) {
            FinderOptionsSheet(
                highlightLabel = stringResource(
                    Res.string.finder_options_highlight,
                    stringResource(Res.string.highlight_color_yellow),
                ),
                highlightEnabled = true,
                matchMode = SearchMatchMode.Substring,
                matchModeEnabled = true,
                onMatchMode = {},
                liveAreaLabel = stringResource(Res.string.scan_area_option_full_frame),
                showImport = true,
                showListTransfer = true,
                listTransferHelp = stringResource(Res.string.search_list_transfer_help),
                importListEnabled = true,
                exportListEnabled = false,
                onImportList = ::ignore,
                onExportList = ::ignore,
                onHighlight = ::ignore,
                onLiveArea = ::ignore,
                onImport = ::ignore,
                onHelp = ::ignore,
                onDismissRequest = ::ignore,
            )
        }
    }

    private fun dialogNodes(root: SemanticsNode): Sequence<SemanticsNode> = sequence {
        yield(root)
        root.children.forEach { child -> yieldAll(dialogNodes(child)) }
    }

    private fun ignore() = Unit
}
