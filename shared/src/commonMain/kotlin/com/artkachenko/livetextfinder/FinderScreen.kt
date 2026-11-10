package com.artkachenko.livetextfinder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.camera.CameraPreview
import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.ImageShareStatus
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.ImportedImagePicker
import com.artkachenko.livetextfinder.camera.ImportedImagePreview
import com.artkachenko.livetextfinder.camera.LiveCaptureControls
import com.artkachenko.livetextfinder.camera.LiveSearchAreaLifecycle
import com.artkachenko.livetextfinder.camera.allowsLiveCamera
import com.artkachenko.livetextfinder.camera.liveSearchAreaAvailable
import com.artkachenko.livetextfinder.camera.rememberFrozenFrameOwner
import com.artkachenko.livetextfinder.camera.rememberImageShareOwner
import com.artkachenko.livetextfinder.camera.rememberImportedImageOwner
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.isCameraRunning
import com.artkachenko.livetextfinder.search.NormalizedSearchArea
import com.artkachenko.livetextfinder.search.SearchPresentationOwner
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.normalizeRecognizedText
import com.artkachenko.livetextfinder.search.projectFrozenHighlights
import com.artkachenko.livetextfinder.search.projectHighlights
import com.artkachenko.livetextfinder.search.projectImportedHighlights
import com.artkachenko.livetextfinder.search.rememberSearchListTransferOwner
import com.artkachenko.livetextfinder.search.searchRecognizedContent
import com.artkachenko.livetextfinder.search.searchRecognizedText
import com.artkachenko.livetextfinder.search.searchTermSummaries
import com.artkachenko.livetextfinder.ui.ConfirmDestructiveDialog
import com.artkachenko.livetextfinder.ui.ContextMessage
import com.artkachenko.livetextfinder.ui.FinderCaptureActions
import com.artkachenko.livetextfinder.ui.FinderCaptureControls
import com.artkachenko.livetextfinder.ui.FinderContentAssembly
import com.artkachenko.livetextfinder.ui.FinderLiveAppBar
import com.artkachenko.livetextfinder.ui.FinderLiveDock
import com.artkachenko.livetextfinder.ui.FinderOptionsSheet
import com.artkachenko.livetextfinder.ui.FinderSearchBar
import com.artkachenko.livetextfinder.ui.FinderSearchControls
import com.artkachenko.livetextfinder.ui.FinderSearchLists
import com.artkachenko.livetextfinder.ui.FinderSpacing
import com.artkachenko.livetextfinder.ui.HighlightColorDialog
import com.artkachenko.livetextfinder.ui.LiveCaptureControlRow
import com.artkachenko.livetextfinder.ui.RetainedInspectionActionDispatcher
import com.artkachenko.livetextfinder.ui.SearchTermResultsDialog
import com.artkachenko.livetextfinder.ui.StillInspectionViewport
import com.artkachenko.livetextfinder.ui.localizedHighlightColorName
import com.artkachenko.livetextfinder.utils.MonotonicClock
import kotlinx.coroutines.delay
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.capture_cancel_freeze
import livetextfinder.shared.generated.resources.capture_cancel_import
import livetextfinder.shared.generated.resources.capture_freeze
import livetextfinder.shared.generated.resources.capture_resume
import livetextfinder.shared.generated.resources.capture_return_to_camera
import livetextfinder.shared.generated.resources.finder_options_highlight
import livetextfinder.shared.generated.resources.inspection_lists
import livetextfinder.shared.generated.resources.inspection_results
import livetextfinder.shared.generated.resources.inspection_return_frozen
import livetextfinder.shared.generated.resources.saved_search_reset_confirm
import livetextfinder.shared.generated.resources.saved_search_reset_message
import livetextfinder.shared.generated.resources.saved_search_reset_title
import livetextfinder.shared.generated.resources.scan_area_option_custom
import livetextfinder.shared.generated.resources.scan_area_option_full_frame
import livetextfinder.shared.generated.resources.search_list_delete
import livetextfinder.shared.generated.resources.search_list_delete_fallback_name
import livetextfinder.shared.generated.resources.search_list_delete_message
import livetextfinder.shared.generated.resources.search_list_delete_title
import livetextfinder.shared.generated.resources.search_list_transfer_cancel
import livetextfinder.shared.generated.resources.search_list_transfer_help
import livetextfinder.shared.generated.resources.search_lists_close
import livetextfinder.shared.generated.resources.search_results_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * The scanner screen: the live preview or a retained image, the search dock, the saved-list
 * manager and every dialog. It owns the screen's state holders and wires them to the UI.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FinderScreen(session: SearchSessionController) {
    var state by remember { mutableStateOf(OcrUiState()) }
    val importedOwner = rememberImportedImageOwner()
    ImportedImagePicker(importedOwner)
    val importedState = importedOwner.state.value
    val sessionState = session.state.value
    val activeSearch = sessionState.activeSearch
    val query = sessionState.persisted.manualQuery
    val selectedList = sessionState.activeList
    val transferOwner = rememberSearchListTransferOwner()
    val listManager = remember(session, transferOwner) {
        SearchListManagerState(session, transferOwner::contextChanged)
    }
    val transferCopy = searchListTransferCopy()
    SideEffect {
        transferOwner.updateContext(
            sessionState = sessionState,
            managerOpen = listManager.isOpen,
            editor = listManager.editor,
            contextRevision = listManager.contextRevision,
            copy = transferCopy,
            isContextCurrent = { session.state.value == sessionState },
            onImportDraft = listManager::acceptImportedDraft,
        )
    }
    val transferState = transferOwner.state.value
    var resetConfirm by remember { mutableStateOf(false) }
    var colorDialogOpen by remember { mutableStateOf(false) }
    val liveArea = remember { LiveSearchAreaState() }
    val editorDraft = liveArea.editorDraft
    val areaEditorOpen = editorDraft != null
    val areaEditorDraft = editorDraft?.area
    val areaEditorToken = editorDraft?.token ?: 0L
    var helpDialogOpen by remember { mutableStateOf(false) }
    var searchResultsDialogOpen by remember(activeSearch.identity) { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    val previewSizeState = remember { mutableStateOf(IntSize.Zero) }
    var previewSize by previewSizeState
    val rootSizeState = remember { mutableStateOf(IntSize.Zero) }
    val contentScrollState = rememberScrollState()
    var rootSize by rootSizeState
    var graceTimerRevision by remember { mutableIntStateOf(0) }
    val frozenOwner = rememberFrozenFrameOwner()
    val shareOwner = rememberImageShareOwner()
    val freezeState = frozenOwner.state.value
    LiveSearchAreaLifecycle(liveArea::reset)
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val isContentVisible = {
        val currentRootSize = rootSizeState.value
        val isCompact =
            (currentRootSize != IntSize.Zero &&
                currentRootSize.height / density.density < 500f) ||
                density.fontScale >= 1.3f
        !listManager.isOpen && !isCompact && imeInsets.getBottom(density) == 0
    }
    val captureContextCurrent = {
        val phase = importedOwner.state.value.phase
        frozenOwner.state.value.mode == FreezeMode.Live &&
            phase.allowsLiveCamera &&
            liveArea.isIdle &&
            isContentVisible()
    }
    val captureSourceCurrent = {
        val phase = importedOwner.state.value.phase
        frozenOwner.state.value.mode != FreezeMode.Frozen &&
            phase.allowsLiveCamera &&
            isContentVisible()
    }
    val latestCaptureContext by rememberUpdatedState(captureContextCurrent)
    val latestCaptureSource by rememberUpdatedState(captureSourceCurrent)
    val liveCaptureControls = remember {
        LiveCaptureControls(
            isContextCurrent = { latestCaptureContext() },
            isCaptureContextCurrent = { latestCaptureSource() },
        )
    }
    val liveCaptureState = liveCaptureControls.state.value
    val presentationOwner = remember { SearchPresentationOwner() }
    val presentation = remember(
        activeSearch,
        state,
        graceTimerRevision,
        liveArea.area,
        liveArea.revision,
        previewSize,
    ) {
        presentationOwner.present(
            activeSearch,
            state,
            MonotonicClock.nowMillis(),
            liveArea.area,
            liveArea.revision,
            previewSize.width,
            previewSize.height,
        )
    }
    val frozenFrame = freezeState.frame
    val frozenSearch = remember(activeSearch, frozenFrame) {
        frozenFrame?.let { frame ->
            searchRecognizedText(
                activeSearch,
                OcrUiState(OcrStatus.TextRecognized, snapshot = frame.snapshot),
            )
        }
    }
    val cameraSearch = frozenSearch ?: presentation.search
    val importedSearch = remember(activeSearch, importedState.snapshot) {
        importedState.snapshot?.let { snapshot ->
            searchRecognizedContent(activeSearch, snapshot)
        }
    }
    val search = importedSearch ?: cameraSearch
    val stillSource = stillSourceOf(importedState, freezeState)
    val sourceIdentity = stillSource?.identity
    val editorCanRemainOpen = areaEditorOpen && sourceIdentity == null &&
        freezeState.mode == FreezeMode.Live &&
        importedState.phase.allowsLiveCamera &&
        liveSearchAreaAvailable() && isContentVisible()
    val editorGestureEnabled = editorCanRemainOpen &&
        previewSize.width > 0 && previewSize.height > 0 &&
        state.status.isCameraRunning
    LaunchedEffect(areaEditorOpen, editorCanRemainOpen) {
        if (areaEditorOpen && !editorCanRemainOpen) {
            liveArea.cancelEditing()
        }
    }
    LiveAreaBackHandler(areaEditorOpen) {
        liveArea.cancelEditor(areaEditorToken)
    }
    val inspection = stillInspectionState(
        source = stillSource,
        search = search,
        searchIdentity = activeSearch.identity,
        highlightColor = sessionState.persisted.highlightColor,
        viewportState = previewSizeState,
        importedOwner = importedOwner,
        frozenOwner = frozenOwner,
        session = session,
        isContentVisible = isContentVisible,
    )
    val copySession = inspection.copySession
    val matchRows = inspection.rows
    val termSummaries = remember(search, sessionState.persisted.highlightColor) {
        searchTermSummaries(search, sessionState.persisted.highlightColor)
    }
    LaunchedEffect(sourceIdentity, activeSearch.identity, freezeState.mode) {
        if (sourceIdentity == null) {
            inspection.clearSelection()
        }
    }
    LaunchedEffect(
        sourceIdentity,
        activeSearch.identity,
        sessionState.persisted.highlightColor,
    ) {
        shareOwner.updateCurrent(
            sourceIdentity,
            activeSearch.identity,
            sessionState.persisted.highlightColor,
        )
    }
    LaunchedEffect(presentation.expiresAtMillis) {
        val deadline = presentation.expiresAtMillis ?: return@LaunchedEffect
        val wait = deadline - MonotonicClock.nowMillis()
        if (wait > 0) delay(wait)
        graceTimerRevision++
    }
    val highlights = remember(
        search,
        importedState.snapshot,
        state.projection,
        previewSize,
        freezeState.mode,
    ) {
        if (importedState.phase == ImportedImagePhase.Imported &&
            importedState.snapshot != null
        ) {
            projectImportedHighlights(
                search,
                importedState.snapshot,
                previewSize.width.toFloat(),
                previewSize.height.toFloat(),
            )
        } else if (freezeState.mode == FreezeMode.Frozen) {
            projectFrozenHighlights(
                search,
                previewSize.width.toFloat(),
                previewSize.height.toFloat(),
            )
        } else {
            projectHighlights(
                search,
                state.projection,
                previewSize.width,
                previewSize.height,
            )
        }
    }
    // This is the only IME inset owner. The weighted preview receives the remaining
    // height directly, so OCR status changes do not recalculate its available space.
    val shouldShowViews = isContentVisible()
    val resultsAvailable = hasAvailableSearchResults(
        search = search,
        currentIdentity = activeSearch.identity,
        importedState = importedState,
        freezeState = freezeState,
        ocrState = state,
        presentation = presentation,
        areaSelected = liveArea.area != null,
        currentAreaRevision = liveArea.revision,
        shouldShowViews = shouldShowViews,
        nowMillis = MonotonicClock.nowMillis(),
    )
    val unavailableGuidance = unavailableResultsGuidance(
        resultsAvailable = resultsAvailable,
        hasUsableTerms = activeSearch.terms.any {
            normalizeRecognizedText(it.text).isNotEmpty()
        },
        importedState = importedState,
        freezeState = freezeState,
        ocrState = state,
        shouldShowViews = shouldShowViews,
        areaSelected = liveArea.area != null,
        areaReady = presentation.areaReady,
    )
    val unavailableExplanation = unavailableResultsExplanation(unavailableGuidance)
    val hasLiveCaptureControls = importedState.phase.allowsLiveCamera
    val captureLabel = if (!hasLiveCaptureControls) {
        null
    } else {
        when (freezeState.mode) {
            FreezeMode.Live -> stringResource(Res.string.capture_freeze)
            FreezeMode.Freezing -> null
            FreezeMode.Frozen -> stringResource(Res.string.capture_resume)
        }
    }
    val pendingCaptureLabel = if (
        hasLiveCaptureControls && freezeState.mode == FreezeMode.Freezing
    ) {
        stringResource(Res.string.capture_cancel_freeze)
    } else {
        null
    }
    val liveAreaLabel = if (
        hasLiveCaptureControls && freezeState.mode == FreezeMode.Live &&
        liveSearchAreaAvailable()
    ) {
        if (liveArea.area == null) {
            stringResource(Res.string.scan_area_option_full_frame)
        } else {
            stringResource(Res.string.scan_area_option_custom)
        }
    } else {
        null
    }
    val captureEnabled = freezeState.mode != FreezeMode.Live ||
        !liveCaptureState.zoomPending && state.status.isCameraRunning
    val searchListsLabel = if (listManager.isOpen) {
        stringResource(Res.string.search_lists_close)
    } else {
        stringResource(Res.string.inspection_lists)
    }
    val resultsSummary = if (resultsAvailable) {
        stringResource(Res.string.inspection_results, search.occurrences.size)
    } else {
        stringResource(Res.string.search_results_unavailable)
    }
    val directSecondaryActionLabel = when (importedState.phase) {
        ImportedImagePhase.Imported ->
            stringResource(Res.string.capture_return_to_camera)
        ImportedImagePhase.Picking,
        ImportedImagePhase.Reading,
        ImportedImagePhase.Recognizing ->
            stringResource(Res.string.capture_cancel_import)
        ImportedImagePhase.Unsupported,
        ImportedImagePhase.Live,
        ImportedImagePhase.Error -> null
    }
    val onManualQueryChange: (String) -> Unit = { value ->
        listManager.markContextChanged()
        session.setManualQuery(value)
    }
    val onShowTerms = {
        RetainedInspectionActionDispatcher.showTerms(
            { searchResultsDialogOpen = it },
            { copySession.updateDialogOpen(it) },
        )
    }
    val onUseManualSearch: () -> Unit = { listManager.selectList(null) }
    val onCapture: () -> Unit = {
        when (freezeState.mode) {
            FreezeMode.Live -> {
                liveArea.reset()
                frozenOwner.freeze(
                    cameraReady = !liveCaptureControls.state.value.zoomPending &&
                        captureContextCurrent() &&
                        state.status.isCameraRunning,
                )
            }
            FreezeMode.Frozen -> {
                liveArea.reset()
                frozenOwner.resume()
            }
            FreezeMode.Freezing -> Unit
        }
    }
    val onSecondaryAction: () -> Unit = {
        when (importedState.phase) {
            ImportedImagePhase.Imported -> {
                liveArea.reset()
                importedOwner.cancel()
            }
            ImportedImagePhase.Picking,
            ImportedImagePhase.Reading,
            ImportedImagePhase.Recognizing -> importedOwner.cancel()
            ImportedImagePhase.Unsupported,
            ImportedImagePhase.Live,
            ImportedImagePhase.Error -> Unit
        }
    }
    val searchBar = FinderSearchBar(
        query = query,
        onQueryChange = onManualQueryChange,
        activeListName = selectedList?.name,
        activeListTermCount = selectedList?.terms?.size ?: 0,
        matchMode = activeSearch.matchMode,
        saving = sessionState.saving,
        searchListsLabel = searchListsLabel,
        onSearchLists = listManager::toggle,
        onTerms = onShowTerms,
        onUseManual = onUseManualSearch,
        onResults = { searchResultsDialogOpen = true },
    )
    val captureActions = FinderCaptureActions(
        captureLabel = captureLabel,
        captureEnabled = captureEnabled,
        onCapture = onCapture,
        pendingCaptureLabel = pendingCaptureLabel,
        onCancelCapture = frozenOwner::cancel,
        secondaryActionLabel = directSecondaryActionLabel,
        onSecondaryAction = onSecondaryAction,
    )
    val layoutMode = finderLayoutMode(
        hasStillSource = stillSource != null,
        managerOpen = listManager.isOpen,
        areaEditing = editorCanRemainOpen,
    )
    val liveScreen = shouldShowViews && layoutMode.isLiveCamera
    val statusContent: @Composable () -> Unit = {
        FinderStatusRegion(
            state = state,
            importedState = importedState,
            freezeState = freezeState,
            sessionState = sessionState,
            search = search,
            presentation = presentation,
            highlights = highlights,
            resultsAvailable = resultsAvailable,
            areaSelected = liveArea.area != null,
            shouldShowViews = shouldShowViews,
            liveStageBand = liveScreen,
            onRetrySavedData = {
                listManager.markContextChanged()
                session.retry()
            },
            onRequestReset = { resetConfirm = true },
        )
    }
    val shareCurrentStillImage: () -> Unit = {
        shareOwner.shareStillImage(
            imported = importedState.phase == ImportedImagePhase.Imported,
            importedOwner = importedOwner,
            frozenOwner = frozenOwner,
            session = session,
            search = search,
        )
    }
    FinderContentAssembly(
        scrollState = contentScrollState,
        scrollable = !shouldShowViews,
        modifier = Modifier.fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .onSizeChanged { rootSize = it },
        topContent = {
            if (layoutMode.usesDockChrome) {
                FinderLiveAppBar(onOptions = { optionsOpen = true })
            }
            Column(
                Modifier.fillMaxWidth().then(
                    if (layoutMode.usesDockChrome) {
                        Modifier
                    } else {
                        Modifier.padding(
                            horizontal = FinderSpacing.large,
                            vertical = FinderSpacing.extraSmall,
                        )
                    },
                ),
                verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
            ) {
                if (!layoutMode.usesDockChrome) {
                    FinderSearchControls(
                        showTitle = shouldShowViews,
                        search = searchBar,
                        resultsLabel = resultsSummary.takeIf { search.query.isNotEmpty() },
                        onOptions = { optionsOpen = true },
                    )
                }
                if (transferState.message != null) {
                    ContextMessage(
                        message = transferState.message,
                        actionLabel = if (transferState.busy) {
                            stringResource(Res.string.search_list_transfer_cancel)
                        } else {
                            null
                        },
                        onAction = if (transferState.busy) transferOwner::cancel else null,
                        isError = transferState.isError,
                    )
                }
                if (!liveScreen) {
                    statusContent()
                }
            }
            if (liveScreen && !editorCanRemainOpen) {
                statusContent()
            }
        },
        managerContent = {
            if (listManager.isOpen) {
                FinderSearchLists(
                    lists = sessionState.persisted.lists,
                    activeListId = sessionState.persisted.activeListId,
                    editor = listManager.editor,
                    canSave = sessionState.loaded && !sessionState.recoveryRequired,
                    onNew = listManager::startNewList,
                    onSelect = listManager::selectList,
                    onEdit = listManager::editList,
                    onDelete = listManager::requestDelete,
                    onNameChange = listManager::updateDraftName,
                    onTermsChange = listManager::updateDraftTerms,
                    onSave = listManager::saveDraft,
                    onCancel = listManager::cancelDraft,
                )
            }
        },
        viewportContent = {
            if (shouldShowViews) {
                StillInspectionViewport(
                    sourceIdentity = inspection.inspectionIdentity,
                    fit = inspection.fit,
                    viewportSize = previewSize,
                    transform = inspection.transform,
                    onTransformChange = { inspection.transform = it },
                    modifier = Modifier.weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                        .testTag("finder-image-viewport")
                        .onSizeChanged { previewSize = it },
                ) {
                    when (importedState.phase) {
                        ImportedImagePhase.Imported -> ImportedImagePreview(
                            importedOwner,
                            Modifier.fillMaxSize(),
                            isContentVisible,
                        )
                        ImportedImagePhase.Picking,
                        ImportedImagePhase.Reading,
                        ImportedImagePhase.Recognizing -> Unit
                        else -> CameraPreview(
                            Modifier.fillMaxSize(),
                            frozenOwner,
                            liveCaptureControls,
                            isContentVisible,
                        ) { state = it }
                    }
                    HighlightOverlay(
                        suppressed = editorCanRemainOpen,
                        search = search,
                        activeSearch = activeSearch,
                        presentation = presentation,
                        ocrState = state,
                        importedState = importedState,
                        freezeState = freezeState,
                        liveArea = liveArea,
                        inspection = inspection,
                        viewportState = previewSizeState,
                        importedOwner = importedOwner,
                        frozenOwner = frozenOwner,
                        session = session,
                        isContentVisible = isContentVisible,
                        modifier = Modifier.matchParentSize(),
                    )
                    if (editorGestureEnabled) {
                        LiveSearchAreaEditorOverlay(
                            area = areaEditorDraft ?: NormalizedSearchArea(0f, 0f, 1f, 1f),
                            viewportSize = previewSize,
                            onAreaChange = { liveArea.updateEditor(areaEditorToken, it) },
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    if (!editorCanRemainOpen && liveArea.area != null &&
                        liveArea.owner.revision == presentation.areaRevision &&
                        liveArea.owner.area == liveArea.area &&
                        importedState.phase != ImportedImagePhase.Imported &&
                        importedOwner.state.value.phase != ImportedImagePhase.Imported &&
                        frozenOwner.state.value.mode == FreezeMode.Live &&
                        isContentVisible()
                    ) {
                        ScanAreaBadge(Modifier.align(Alignment.TopStart))
                    }
                    if (sourceIdentity == null) {
                        LiveCaptureControlRow(
                            controls = liveCaptureControls,
                            contextEnabled = captureContextCurrent(),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                        )
                    }
                }
            }
        },
        footerContent = {
            when (layoutMode) {
                FinderLayoutMode.AreaEditing -> {
                    ScanAreaEditBar(
                        liveArea = liveArea,
                        editorToken = areaEditorToken,
                        importedOwner = importedOwner,
                        frozenOwner = frozenOwner,
                        isContentVisible = isContentVisible,
                    )
                }
                FinderLayoutMode.ListManager -> {
                    FinderCaptureControls(captureActions)
                }
                FinderLayoutMode.StillInspection -> {
                    StillInspectionFooter(
                        inspection = inspection,
                        shareMessage = shareOwner.state.value.message,
                        shouldShowViews = shouldShowViews,
                        resultsAvailable = resultsAvailable,
                    )
                }
                FinderLayoutMode.Live -> Unit
            }
            if (layoutMode.showsDock) {
                val dockFocusManager = LocalFocusManager.current
                val dockKeyboardController = LocalSoftwareKeyboardController.current
                FinderLiveDock(
                    search = searchBar,
                    capture = captureActions,
                    onOccurrences = {
                        RetainedInspectionActionDispatcher.showOccurrences(
                            dockFocusManager::clearFocus,
                            { dockKeyboardController?.hide() },
                            { searchResultsDialogOpen = it },
                            { copySession.updateDialogOpen(it) },
                        )
                    },
                    resultsLabel = resultsSummary.takeIf {
                        selectedList != null || search.query.isNotEmpty()
                    },
                    occurrencesEnabled = resultsAvailable && matchRows.isNotEmpty(),
                    returnLabel = when (stillSource) {
                        is StillSource.Imported ->
                            stringResource(Res.string.capture_return_to_camera)
                        is StillSource.Frozen ->
                            stringResource(Res.string.inspection_return_frozen)
                        null -> null
                    },
                    onReturn = {
                        RetainedInspectionActionDispatcher.returnToSource(
                            importedState.phase == ImportedImagePhase.Imported,
                            liveArea::reset,
                            importedOwner::cancel,
                            frozenOwner::resume,
                        )
                    },
                    onShare = shareCurrentStillImage,
                    onCancelShare = shareOwner::cancel,
                    sharePreparing = shareOwner.state.value.status ==
                        ImageShareStatus.Preparing,
                )
            }
        },
    )
    if (copySession.dialogOpen && sourceIdentity != null) {
        StillInspectionMatchesDialog(
            inspection = inspection,
            shouldShowViews = shouldShowViews,
            resultsAvailable = resultsAvailable,
        )
    }
    if (searchResultsDialogOpen) {
        SearchTermResultsDialog(
            terms = termSummaries,
            resultsAvailable = resultsAvailable,
            unavailableExplanation = unavailableExplanation,
            canLocate = shouldShowViews && sourceIdentity != null && resultsAvailable,
            onSelect = { termId ->
                inspection.locateFirstForTerm(termId) { searchResultsDialogOpen = false }
            },
            onDismiss = { searchResultsDialogOpen = false },
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
    if (resetConfirm) {
        ConfirmDestructiveDialog(
            title = stringResource(Res.string.saved_search_reset_title),
            message = stringResource(Res.string.saved_search_reset_message),
            confirmLabel = stringResource(Res.string.saved_search_reset_confirm),
            onConfirm = {
                resetConfirm = false
                listManager.markContextChanged()
                session.retry()
            },
            onDismiss = { resetConfirm = false },
        )
    }
    LiveSearchAreaDialogHost(liveArea)
    if (colorDialogOpen) {
        HighlightColorDialog(
            selectedColor = sessionState.persisted.highlightColor,
            enabled = sessionState.loaded && !sessionState.recoveryRequired,
            onSelectColor = { color ->
                listManager.markContextChanged()
                session.setHighlightColor(color)
            },
            onDismissRequest = { colorDialogOpen = false },
        )
    }
    if (helpDialogOpen) {
        UsageHelpDialog(onDismissRequest = { helpDialogOpen = false })
    }
    if (optionsOpen) {
        FinderOptionsSheet(
            highlightLabel = stringResource(
                Res.string.finder_options_highlight,
                localizedHighlightColorName(sessionState.persisted.highlightColor),
            ),
            highlightEnabled = sessionState.loaded && !sessionState.recoveryRequired,
            matchMode = sessionState.persisted.matchMode,
            matchModeEnabled = sessionState.loaded && !sessionState.recoveryRequired,
            onMatchMode = { mode ->
                listManager.markContextChanged()
                session.setMatchMode(mode)
            },
            liveAreaLabel = liveAreaLabel,
            showImport = importedState.phase.allowsLiveCamera,
            showListTransfer = transferState.supported,
            listTransferHelp = if (transferState.supported) {
                stringResource(Res.string.search_list_transfer_help)
            } else {
                null
            },
            importListEnabled = transferState.importEnabled,
            exportListEnabled = transferState.exportEnabled,
            onImportList = {
                optionsOpen = false
                transferOwner.importList()
            },
            onExportList = {
                optionsOpen = false
                transferOwner.exportSelectedList()
            },
            onHighlight = {
                optionsOpen = false
                colorDialogOpen = true
            },
            onLiveArea = {
                optionsOpen = false
                val latestPhase = importedOwner.state.value.phase
                val canDirectEdit = !liveCaptureControls.state.value.zoomPending &&
                    frozenOwner.state.value.mode == FreezeMode.Live &&
                    latestPhase.allowsLiveCamera &&
                    liveSearchAreaAvailable() && isContentVisible()
                if (canDirectEdit) {
                    liveArea.beginEditor(
                        liveArea.area ?: NormalizedSearchArea.CENTERED_DEFAULT,
                    )
                } else {
                    liveArea.openDialog(usesDraft = false, editorToken = 0L)
                }
            },
            onImport = {
                optionsOpen = false
                liveArea.reset()
                if (freezeState.mode == FreezeMode.Freezing) {
                    frozenOwner.cancel()
                }
                frozenOwner.resume()
                importedOwner.beginPicker()
            },
            onHelp = {
                optionsOpen = false
                helpDialogOpen = true
            },
            onDismissRequest = { optionsOpen = false },
        )
    }
}
