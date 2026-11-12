package com.artkachenko.livetextfinder.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.artkachenko.livetextfinder.inspection.StillInspectionMatchesDialog
import com.artkachenko.livetextfinder.inspection.StillInspectionState
import com.artkachenko.livetextfinder.lists.SearchListManagerState
import com.artkachenko.livetextfinder.scanarea.LiveSearchAreaDialogHost
import com.artkachenko.livetextfinder.scanarea.LiveSearchAreaState
import com.artkachenko.livetextfinder.search.SearchListTransferState
import com.artkachenko.livetextfinder.search.SearchSessionState
import com.artkachenko.livetextfinder.ui.ConfirmDestructiveDialog
import com.artkachenko.livetextfinder.ui.FinderOptionsSheet
import com.artkachenko.livetextfinder.ui.HighlightColorDialog
import com.artkachenko.livetextfinder.ui.SearchTermResultsDialog
import com.artkachenko.livetextfinder.ui.UsageHelpDialog
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.saved_search_reset_confirm
import livetextfinder.shared.generated.resources.saved_search_reset_message
import livetextfinder.shared.generated.resources.saved_search_reset_title
import livetextfinder.shared.generated.resources.search_list_delete
import livetextfinder.shared.generated.resources.search_list_delete_fallback_name
import livetextfinder.shared.generated.resources.search_list_delete_message
import livetextfinder.shared.generated.resources.search_list_delete_title
import org.jetbrains.compose.resources.stringResource

/** Which of the main screen's own dialogs and sheets are open. */
internal class FinderDialogsState {
    var optionsOpen by mutableStateOf(false)
    var highlightColorOpen by mutableStateOf(false)
    var helpOpen by mutableStateOf(false)
    var resetConfirmOpen by mutableStateOf(false)

    internal var termResultsState: MutableState<Boolean> = mutableStateOf(false)

    /** The per-term results dialog; it closes whenever the active search changes. */
    var termResultsOpen: Boolean
        get() = termResultsState.value
        set(value) {
            termResultsState.value = value
        }
}

/** Remembers the dialog state; the term results dialog is reset for a new [searchIdentity]. */
@Composable
internal fun rememberFinderDialogsState(searchIdentity: String): FinderDialogsState {
    val termResultsState = remember(searchIdentity) { mutableStateOf(false) }
    val state = remember { FinderDialogsState() }
    state.termResultsState = termResultsState
    return state
}

/**
 * Hosts every dialog and sheet of the main screen: matches, per-term results, delete and reset
 * confirmations, the scan-area precision dialog, highlight colour, help and options.
 */
@Composable
internal fun FinderDialogs(
    dialogs: FinderDialogsState,
    sessionState: SearchSessionState,
    searchState: FinderSearchState,
    inspection: StillInspectionState,
    listManager: SearchListManagerState,
    liveArea: LiveSearchAreaState,
    transferState: SearchListTransferState,
    actions: FinderSourceActions,
    liveAreaLabel: String?,
    showImport: Boolean,
    shouldShowViews: Boolean,
    onImportList: () -> Unit,
    onExportList: () -> Unit,
) {
    val resultsAvailable = searchState.resultsAvailable
    val hasStillSource = inspection.source != null
    val savedSearchEditable = sessionState.loaded && !sessionState.recoveryRequired
    if (inspection.copySession.dialogOpen && hasStillSource) {
        StillInspectionMatchesDialog(
            inspection = inspection,
            shouldShowViews = shouldShowViews,
            resultsAvailable = resultsAvailable,
        )
    }
    if (dialogs.termResultsOpen) {
        SearchTermResultsDialog(
            terms = searchState.termSummaries,
            resultsAvailable = resultsAvailable,
            unavailableExplanation = searchState.unavailableExplanation,
            canLocate = shouldShowViews && hasStillSource && resultsAvailable,
            onSelect = { termId ->
                inspection.locateFirstForTerm(termId) { dialogs.termResultsOpen = false }
            },
            onDismiss = { dialogs.termResultsOpen = false },
        )
    }
    listManager.pendingDeleteId?.let { id ->
        val name = sessionState.persisted.lists.firstOrNull { list -> list.id == id }
            ?.name ?: stringResource(Res.string.search_list_delete_fallback_name)
        ConfirmDestructiveDialog(
            title = stringResource(Res.string.search_list_delete_title),
            message = stringResource(Res.string.search_list_delete_message, name),
            confirmLabel = stringResource(Res.string.search_list_delete),
            onConfirm = { listManager.confirmDelete(id) },
            onDismiss = listManager::dismissDelete,
        )
    }
    if (dialogs.resetConfirmOpen) {
        ConfirmDestructiveDialog(
            title = stringResource(Res.string.saved_search_reset_title),
            message = stringResource(Res.string.saved_search_reset_message),
            confirmLabel = stringResource(Res.string.saved_search_reset_confirm),
            onConfirm = {
                dialogs.resetConfirmOpen = false
                listManager.retrySavedSearches()
            },
            onDismiss = { dialogs.resetConfirmOpen = false },
        )
    }
    LiveSearchAreaDialogHost(liveArea)
    if (dialogs.highlightColorOpen) {
        HighlightColorDialog(
            selectedColor = sessionState.persisted.highlightColor,
            enabled = savedSearchEditable,
            onSelectColor = listManager::setHighlightColor,
            onDismissRequest = { dialogs.highlightColorOpen = false },
        )
    }
    if (dialogs.helpOpen) {
        UsageHelpDialog(onDismissRequest = { dialogs.helpOpen = false })
    }
    if (dialogs.optionsOpen) {
        FinderOptionsSheet(
            sessionState = sessionState,
            transferState = transferState,
            liveAreaLabel = liveAreaLabel,
            showImport = showImport,
            onMatchMode = listManager::setMatchMode,
            onImportList = onImportList,
            onExportList = onExportList,
            onHighlight = { dialogs.highlightColorOpen = true },
            onLiveArea = actions::startAreaEditing,
            onImport = actions::beginImport,
            onHelp = { dialogs.helpOpen = true },
            onDismiss = { dialogs.optionsOpen = false },
        )
    }
}
