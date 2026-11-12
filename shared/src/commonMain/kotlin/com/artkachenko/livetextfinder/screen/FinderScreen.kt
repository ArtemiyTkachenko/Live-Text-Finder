package com.artkachenko.livetextfinder.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.ImageShareStatus
import com.artkachenko.livetextfinder.camera.LiveSearchAreaLifecycle
import com.artkachenko.livetextfinder.camera.allowsLiveCamera
import com.artkachenko.livetextfinder.camera.liveSearchAreaAvailable
import com.artkachenko.livetextfinder.camera.rememberImageShareOwner
import com.artkachenko.livetextfinder.inspection.stillInspectionState
import com.artkachenko.livetextfinder.inspection.stillSourceOf
import com.artkachenko.livetextfinder.lists.SavedSearchEditingState
import com.artkachenko.livetextfinder.lists.SearchListTransferContextEffect
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.isCameraRunning
import com.artkachenko.livetextfinder.scanarea.LiveAreaBackHandler
import com.artkachenko.livetextfinder.scanarea.LiveSearchAreaState
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.rememberSearchListTransferOwner
import com.artkachenko.livetextfinder.status.FinderStatusRegion
import com.artkachenko.livetextfinder.ui.FinderCaptureActions
import com.artkachenko.livetextfinder.ui.FinderContentAssembly
import com.artkachenko.livetextfinder.ui.FinderSearchBar
import com.artkachenko.livetextfinder.ui.FinderSearchLists
import com.artkachenko.livetextfinder.ui.RetainedInspectionActionDispatcher

/**
 * The scanner screen: the live preview or a retained image, the search dock, the saved-list
 * manager and every dialog.
 *
 * It creates the screen's state holders, derives what this composition displays and passes
 * both to the region composables. Displayed values are captured here; owners are reached
 * through [FinderSourceContext] where a callback must compare against current state.
 */
@Composable
internal fun FinderScreen(session: SearchSessionController) {
    var ocrState by remember { mutableStateOf(OcrUiState()) }
    val sessionState = session.state.value
    val activeSearch = sessionState.activeSearch
    val selectedList = sessionState.activeList
    val highlightColor = sessionState.persisted.highlightColor

    val transferOwner = rememberSearchListTransferOwner()
    val searchEditing = rememberSaveable(
        session,
        transferOwner,
        saver = SavedSearchEditingState.saver(session, transferOwner::contextChanged),
    ) {
        SavedSearchEditingState(session, transferOwner::contextChanged)
    }
    SearchListTransferContextEffect(transferOwner, session, sessionState, searchEditing)
    val transferState = transferOwner.state.value

    val liveArea = remember { LiveSearchAreaState() }
    LiveSearchAreaLifecycle(liveArea::reset)
    val viewportState = remember { mutableStateOf(IntSize.Zero) }
    val rootSizeState = remember { mutableStateOf(IntSize.Zero) }
    val context = finderSourceContext(session, searchEditing, liveArea, rootSizeState)
    val shareOwner = rememberImageShareOwner()
    val importedState = context.importedOwner.state.value
    val freezeState = context.frozenOwner.state.value
    val shouldShowViews = context.isContentVisible()

    val searchState = finderSearchState(
        sessionState = sessionState,
        ocrState = ocrState,
        importedState = importedState,
        freezeState = freezeState,
        liveArea = liveArea,
        viewport = viewportState.value,
        shouldShowViews = shouldShowViews,
    )
    val search = searchState.search
    val stillSource = stillSourceOf(importedState, freezeState)
    val sourceIdentity = stillSource?.identity

    val editorDraft = liveArea.editorDraft
    val areaEditorOpen = editorDraft != null
    val areaEditing = areaEditorOpen && stillSource == null &&
        freezeState.mode == FreezeMode.Live &&
        importedState.phase.allowsLiveCamera &&
        liveSearchAreaAvailable() && shouldShowViews
    val areaGestureEnabled = areaEditing &&
        viewportState.value.width > 0 && viewportState.value.height > 0 &&
        ocrState.status.isCameraRunning
    LaunchedEffect(areaEditorOpen, areaEditing) {
        if (areaEditorOpen && !areaEditing) {
            liveArea.cancelEditing()
        }
    }
    LiveAreaBackHandler(areaEditorOpen) {
        liveArea.cancelEditor(editorDraft?.token ?: 0L)
    }

    val inspection = stillInspectionState(
        source = stillSource,
        search = search,
        searchIdentity = activeSearch.identity,
        highlightColor = highlightColor,
        viewportState = viewportState,
        context = context,
    )
    LaunchedEffect(sourceIdentity, activeSearch.identity, freezeState.mode) {
        if (sourceIdentity == null) {
            inspection.clearSelection()
        }
    }
    LaunchedEffect(sourceIdentity, activeSearch.identity, highlightColor) {
        shareOwner.updateCurrent(sourceIdentity, activeSearch.identity, highlightColor)
    }

    val dialogs = rememberFinderDialogsState(activeSearch.identity)
    val labels = finderLabels(
        importedPhase = importedState.phase,
        freezeMode = freezeState.mode,
        stillSource = stillSource,
        areaSelected = liveArea.area != null,
        managerOpen = searchEditing.isOpen,
        hasSearch = selectedList != null || search.query.isNotEmpty(),
        resultsAvailable = searchState.resultsAvailable,
        occurrenceCount = search.occurrences.size,
    )
    val actions = FinderSourceActions(
        context = context,
        liveArea = liveArea,
        shareOwner = shareOwner,
        importedState = importedState,
        freezeState = freezeState,
        ocrState = ocrState,
        search = search,
    )
    val searchBar = FinderSearchBar(
        query = sessionState.persisted.manualQuery,
        onQueryChange = searchEditing::setManualQuery,
        activeListName = selectedList?.name,
        activeListTermCount = selectedList?.terms?.size ?: 0,
        matchMode = activeSearch.matchMode,
        saving = sessionState.saving,
        searchListsLabel = labels.searchLists,
        onSearchLists = searchEditing::toggle,
        onTerms = {
            RetainedInspectionActionDispatcher.showTerms(
                setTermsOpen = { dialogs.termResultsOpen = it },
                setOccurrencesOpen = { inspection.copySession.updateDialogOpen(it) },
            )
        },
        onUseManual = { searchEditing.selectList(null) },
        onResults = { dialogs.termResultsOpen = true },
    )
    val captureActions = FinderCaptureActions(
        captureLabel = labels.capture,
        captureEnabled = actions.captureEnabled,
        onCapture = actions::capture,
        pendingCaptureLabel = labels.pendingCapture,
        onCancelCapture = actions::cancelCapture,
        secondaryActionLabel = labels.secondaryAction,
        onSecondaryAction = actions::leaveOrCancelImport,
    )
    val layoutMode = finderLayoutMode(
        hasStillSource = stillSource != null,
        managerOpen = searchEditing.isOpen,
        areaEditing = areaEditing,
    )

    val snackbarHostState = remember { SnackbarHostState() }
    var footerTop by remember { mutableFloatStateOf(0f) }
    FinderFeedbackEffects(
        hostState = snackbarHostState,
        copySession = inspection.copySession,
        shareState = shareOwner.state,
        transferState = transferOwner.state,
    )
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        // This column is the only IME inset owner. The weighted viewport receives the
        // remaining height directly, so status changes do not recalculate its space.
        FinderContentAssembly(
            scrollState = rememberScrollState(),
            scrollable = !shouldShowViews,
            modifier = Modifier.fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .onSizeChanged { rootSizeState.value = it },
            topContent = {
                FinderTopRegion(
                    layoutMode = layoutMode,
                    shouldShowViews = shouldShowViews,
                    searchBar = searchBar,
                    inlineResultsLabel = labels.results,
                    transferState = transferState,
                    onCancelTransfer = transferOwner::cancel,
                    onOptions = { dialogs.optionsOpen = true },
                    status = {
                        FinderStatusRegion(
                            state = ocrState,
                            importedState = importedState,
                            freezeState = freezeState,
                            sessionState = sessionState,
                            search = search,
                            presentation = searchState.presentation,
                            highlights = searchState.highlights,
                            resultsAvailable = searchState.resultsAvailable,
                            areaSelected = liveArea.area != null,
                            shouldShowViews = shouldShowViews,
                            liveStageBand = shouldShowViews && layoutMode.isLiveCamera,
                            onRetrySavedData = searchEditing::retrySavedSearches,
                            onRequestReset = { dialogs.resetConfirmOpen = true },
                        )
                    },
                )
            },
            managerContent = {
                if (searchEditing.isOpen) {
                    FinderSearchLists(
                        lists = sessionState.persisted.lists,
                        activeListId = sessionState.persisted.activeListId,
                        editor = searchEditing.editor,
                        canSave = sessionState.loaded && !sessionState.recoveryRequired,
                        onNew = searchEditing::startNewList,
                        onSelect = searchEditing::selectList,
                        onEdit = searchEditing::editList,
                        onDelete = searchEditing::requestDelete,
                        onNameChange = searchEditing::updateDraftName,
                        onTermsChange = searchEditing::updateDraftTerms,
                        onSave = searchEditing::saveDraft,
                        onCancel = searchEditing::cancelDraft,
                    )
                }
            },
            viewportContent = {
                if (shouldShowViews) {
                    FinderViewport(
                        inspection = inspection,
                        viewportState = viewportState,
                        searchState = searchState,
                        ocrState = ocrState,
                        onOcrState = { ocrState = it },
                        importedState = importedState,
                        freezeState = freezeState,
                        liveArea = liveArea,
                        context = context,
                        areaEditing = areaEditing,
                        areaGestureEnabled = areaGestureEnabled,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            },
            footerContent = {
                Spacer(Modifier.onGloballyPositioned { footerTop = it.positionInParent().y })
                FinderFooter(
                    layoutMode = layoutMode,
                    searchBar = searchBar,
                    captureActions = captureActions,
                    labels = labels,
                    actions = actions,
                    dialogs = dialogs,
                    inspection = inspection,
                    liveArea = liveArea,
                    context = context,
                    shouldShowViews = shouldShowViews,
                    resultsAvailable = searchState.resultsAvailable,
                    sharePreparing = shareOwner.state.value.status == ImageShareStatus.Preparing,
                )
            },
        )
        // Sits above the footer while the preview is shown, so it never covers the dock.
        val footerHeight = if (shouldShowViews) {
            (rootSizeState.value.height - footerTop).coerceAtLeast(0f)
        } else {
            0f
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .imePadding()
                .padding(bottom = with(density) { footerHeight.toDp() }),
        )
    }
    FinderDialogs(
        dialogs = dialogs,
        sessionState = sessionState,
        searchState = searchState,
        inspection = inspection,
        searchEditing = searchEditing,
        liveArea = liveArea,
        transferState = transferState,
        actions = actions,
        liveAreaLabel = labels.liveArea,
        showImport = importedState.phase.allowsLiveCamera,
        shouldShowViews = shouldShowViews,
        onImportList = transferOwner::importList,
        onExportList = transferOwner::exportSelectedList,
    )
}
