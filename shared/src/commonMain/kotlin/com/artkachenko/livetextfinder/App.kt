package com.artkachenko.livetextfinder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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
import com.artkachenko.livetextfinder.camera.isImportInProgress
import com.artkachenko.livetextfinder.camera.liveSearchAreaAvailable
import com.artkachenko.livetextfinder.camera.rememberFrozenFrameOwner
import com.artkachenko.livetextfinder.camera.rememberImageShareOwner
import com.artkachenko.livetextfinder.camera.rememberImportedImageOwner
import com.artkachenko.livetextfinder.camera.shareFrozen
import com.artkachenko.livetextfinder.camera.shareImported
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.isCameraRunning
import com.artkachenko.livetextfinder.search.Highlights
import com.artkachenko.livetextfinder.search.NormalizedSearchArea
import com.artkachenko.livetextfinder.search.SearchHighlightPalette
import com.artkachenko.livetextfinder.search.SearchListTransferCopy
import com.artkachenko.livetextfinder.search.SearchPresentationOwner
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.StillMatchCopyPayload
import com.artkachenko.livetextfinder.search.drawHighlightOutlines
import com.artkachenko.livetextfinder.search.drawSelectedOccurrenceMarker
import com.artkachenko.livetextfinder.search.drawableElementsForSession
import com.artkachenko.livetextfinder.search.importedHighlightsForDraw
import com.artkachenko.livetextfinder.search.inMemorySearchSessionController
import com.artkachenko.livetextfinder.search.isPresentationCurrentAreaRevision
import com.artkachenko.livetextfinder.search.normalizeRecognizedText
import com.artkachenko.livetextfinder.search.occurrenceBounds
import com.artkachenko.livetextfinder.search.projectFrozenHighlights
import com.artkachenko.livetextfinder.search.projectHighlights
import com.artkachenko.livetextfinder.search.projectImportedHighlights
import com.artkachenko.livetextfinder.search.rememberSearchListTransferOwner
import com.artkachenko.livetextfinder.search.searchRecognizedContent
import com.artkachenko.livetextfinder.search.searchRecognizedText
import com.artkachenko.livetextfinder.search.searchTermSummaries
import com.artkachenko.livetextfinder.ui.ContextMessage
import com.artkachenko.livetextfinder.ui.FinderCaptureControls
import com.artkachenko.livetextfinder.ui.FinderContentAssembly
import com.artkachenko.livetextfinder.ui.FinderLiveAppBar
import com.artkachenko.livetextfinder.ui.FinderLiveDock
import com.artkachenko.livetextfinder.ui.FinderOptionsSheet
import com.artkachenko.livetextfinder.ui.FinderPrimaryAction
import com.artkachenko.livetextfinder.ui.FinderSearchControls
import com.artkachenko.livetextfinder.ui.FinderSearchLists
import com.artkachenko.livetextfinder.ui.FinderSecondaryAction
import com.artkachenko.livetextfinder.ui.FinderSpacing
import com.artkachenko.livetextfinder.ui.HighlightColorDialog
import com.artkachenko.livetextfinder.ui.LiveCaptureControlRow
import com.artkachenko.livetextfinder.ui.LiveTextFinderTheme
import com.artkachenko.livetextfinder.ui.RetainedInspectionActionDispatcher
import com.artkachenko.livetextfinder.ui.SearchTermResultsDialog
import com.artkachenko.livetextfinder.ui.StillInspectionControls
import com.artkachenko.livetextfinder.ui.StillInspectionViewport
import com.artkachenko.livetextfinder.ui.StillMatchNavigationControls
import com.artkachenko.livetextfinder.ui.StillMatchesDialog
import com.artkachenko.livetextfinder.ui.localizedHighlightColorName
import com.artkachenko.livetextfinder.utils.MonotonicClock
import kotlinx.coroutines.delay
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.finder_options_highlight
import livetextfinder.shared.generated.resources.inspection_results
import livetextfinder.shared.generated.resources.inspection_return_frozen
import livetextfinder.shared.generated.resources.search_list_import_fallback_name
import livetextfinder.shared.generated.resources.search_list_transfer_another_busy
import livetextfinder.shared.generated.resources.search_list_transfer_cancel
import livetextfinder.shared.generated.resources.search_list_transfer_choose_destination
import livetextfinder.shared.generated.resources.search_list_transfer_choose_file
import livetextfinder.shared.generated.resources.search_list_transfer_export_canceled
import livetextfinder.shared.generated.resources.search_list_transfer_export_complete
import livetextfinder.shared.generated.resources.search_list_transfer_export_failed
import livetextfinder.shared.generated.resources.search_list_transfer_export_unavailable
import livetextfinder.shared.generated.resources.search_list_transfer_help
import livetextfinder.shared.generated.resources.search_list_transfer_import_canceled
import livetextfinder.shared.generated.resources.search_list_transfer_import_failed
import livetextfinder.shared.generated.resources.search_list_transfer_import_ready
import livetextfinder.shared.generated.resources.search_list_transfer_import_unavailable
import livetextfinder.shared.generated.resources.search_list_transfer_list_invalid
import livetextfinder.shared.generated.resources.search_list_transfer_reading
import livetextfinder.shared.generated.resources.search_list_transfer_saving
import livetextfinder.shared.generated.resources.search_results_locate_hint
import livetextfinder.shared.generated.resources.search_results_unavailable
import org.jetbrains.compose.resources.stringResource

/** Main live text search screen. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(session: SearchSessionController = remember { inMemorySearchSessionController() }) {
    LiveTextFinderTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
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
            val transferFallbackName = stringResource(Res.string.search_list_import_fallback_name)
            val transferCopy = SearchListTransferCopy(
                fallbackName = transferFallbackName,
                chooseFile = stringResource(Res.string.search_list_transfer_choose_file),
                readingFile = stringResource(Res.string.search_list_transfer_reading),
                chooseDestination = stringResource(
                    Res.string.search_list_transfer_choose_destination,
                ),
                savingFile = stringResource(Res.string.search_list_transfer_saving),
                importCanceled = stringResource(Res.string.search_list_transfer_import_canceled),
                exportCanceled = stringResource(Res.string.search_list_transfer_export_canceled),
                importReady = stringResource(Res.string.search_list_transfer_import_ready),
                exportComplete = stringResource(Res.string.search_list_transfer_export_complete),
                anotherTransfer = stringResource(Res.string.search_list_transfer_another_busy),
                importUnavailable = stringResource(
                    Res.string.search_list_transfer_import_unavailable,
                ),
                exportUnavailable = stringResource(
                    Res.string.search_list_transfer_export_unavailable,
                ),
                listCannotExport = stringResource(Res.string.search_list_transfer_list_invalid),
                importFailed = stringResource(Res.string.search_list_transfer_import_failed),
                exportFailed = stringResource(Res.string.search_list_transfer_export_failed),
            )
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
            val inspection = rememberStillInspectionState(
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
                    FreezeMode.Live -> "Freeze frame"
                    FreezeMode.Freezing -> null
                    FreezeMode.Frozen -> "Resume live"
                }
            }
            val pendingCaptureLabel = if (
                hasLiveCaptureControls && freezeState.mode == FreezeMode.Freezing
            ) {
                "Cancel freeze"
            } else {
                null
            }
            val liveAreaLabel = if (
                hasLiveCaptureControls && freezeState.mode == FreezeMode.Live &&
                liveSearchAreaAvailable()
            ) {
                if (liveArea.area == null) "Scan area: Full frame" else "Scan area: Custom"
            } else {
                null
            }
            val captureEnabled = freezeState.mode != FreezeMode.Live ||
                !liveCaptureState.zoomPending && state.status.isCameraRunning
            val searchListsLabel = if (listManager.isOpen) "Close lists" else "Lists"
            val resultsSummary = if (resultsAvailable) {
                stringResource(Res.string.inspection_results, search.occurrences.size)
            } else {
                stringResource(Res.string.search_results_unavailable)
            }
            val directSecondaryActionLabel = when (importedState.phase) {
                ImportedImagePhase.Imported -> "Return to camera"
                ImportedImagePhase.Picking,
                ImportedImagePhase.Reading,
                ImportedImagePhase.Recognizing -> "Cancel import"
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
                if (importedState.phase == ImportedImagePhase.Imported) {
                    shareOwner.shareImported(
                        importedOwner,
                        search,
                        currentSourceIdentity = {
                            importedOwner.state.value.snapshot?.let(::importedSourceIdentity)
                        },
                        currentSearchIdentity = {
                            session.state.value.activeSearch.identity
                        },
                        currentHighlightColor = {
                            session.state.value.persisted.highlightColor
                        },
                    )
                } else {
                    shareOwner.shareFrozen(
                        frozenOwner,
                        search,
                        currentSourceIdentity = {
                            frozenOwner.state.value.frame?.let(::frozenSourceIdentity)
                        },
                        currentSearchIdentity = {
                            session.state.value.activeSearch.identity
                        },
                        currentHighlightColor = {
                            session.state.value.persisted.highlightColor
                        },
                    )
                }
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
                                query = query,
                                onQueryChange = onManualQueryChange,
                                searchListsLabel = searchListsLabel,
                                onSearchLists = listManager::toggle,
                                activeListName = selectedList?.name,
                                activeListTermCount = selectedList?.terms?.size ?: 0,
                                matchMode = activeSearch.matchMode,
                                onTerms = onShowTerms,
                                onUseManual = onUseManualSearch,
                                onOptions = { optionsOpen = true },
                                saving = sessionState.saving,
                                resultsLabel = resultsSummary.takeIf { search.query.isNotEmpty() },
                                onResults = { searchResultsDialogOpen = true },
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
                            Canvas(Modifier.matchParentSize()) {
                                if (editorCanRemainOpen) return@Canvas
                                val currentFreezeState = frozenOwner.state.value
                                val currentSearchIdentity =
                                    session.state.value.activeSearch.identity
                                val currentImportState = importedOwner.state.value
                                val areaIsCurrent =
                                    isPresentationCurrentAreaRevision(
                                        presentation,
                                        liveArea.owner.revision,
                                    ) &&
                                    liveArea.revision == presentation.areaRevision
                                val liveAreaIsDisplayed = areaIsCurrent && liveArea.area != null &&
                                    importedState.phase != ImportedImagePhase.Imported &&
                                    !importedState.phase.isImportInProgress &&
                                    currentFreezeState.mode == FreezeMode.Live &&
                                    currentImportState.phase != ImportedImagePhase.Imported &&
                                    !currentImportState.phase.isImportInProgress &&
                                    liveArea.owner.area == liveArea.area &&
                                    isContentVisible() &&
                                    size.width == previewSize.width.toFloat() &&
                                    size.height == previewSize.height.toFloat() &&
                                    size.width > 0f && size.height > 0f
                                val outlines = if (!areaIsCurrent) {
                                    emptyList()
                                } else if (
                                    importedState.phase == ImportedImagePhase.Imported ||
                                    currentImportState.phase == ImportedImagePhase.Imported
                                ) {
                                    importedHighlightsForDraw(
                                        search,
                                        importedState.snapshot,
                                        currentImportState,
                                        activeSearch.identity,
                                        currentSearchIdentity,
                                        size.width,
                                        size.height,
                                        isContentVisible(),
                                    ).outlines
                                } else if (!isContentVisible()) {
                                    emptyList()
                                } else if (importedState.phase.isImportInProgress) {
                                    emptyList()
                                } else if (freezeState.mode == FreezeMode.Frozen) {
                                    if (currentFreezeState.mode == FreezeMode.Frozen &&
                                        currentFreezeState.frame === frozenFrame &&
                                        currentSearchIdentity == activeSearch.identity
                                    ) {
                                        projectFrozenHighlights(
                                            search,
                                            size.width,
                                            size.height,
                                        ).outlines
                                    } else {
                                        emptyList()
                                    }
                                } else {
                                    if (currentFreezeState.mode == FreezeMode.Live ||
                                        currentFreezeState.mode == FreezeMode.Freezing
                                    ) {
                                        drawableElementsForSession(
                                            session,
                                            activeSearch,
                                            presentation,
                                            state,
                                            MonotonicClock.nowMillis(),
                                            size.width,
                                            size.height,
                                        ).map { it.displayOutline }
                                    } else {
                                        emptyList()
                                    }
                                }
                                val currentHighlightColor =
                                    session.state.value.persisted.highlightColor
                                val termColors = SearchHighlightPalette.colorsByTerm(
                                    activeSearch,
                                    currentHighlightColor,
                                )
                                if (liveAreaIsDisplayed) {
                                    val area = checkNotNull(liveArea.area)
                                    clipRect(
                                        left = area.left * size.width,
                                        top = area.top * size.height,
                                        right = area.right * size.width,
                                        bottom = area.bottom * size.height,
                                    ) {
                                        drawHighlightOutlines(
                                            outlines,
                                            currentHighlightColor,
                                            termColors,
                                        )
                                    }
                                } else {
                                    drawHighlightOutlines(
                                        outlines,
                                        currentHighlightColor,
                                        termColors,
                                    )
                                }
                                val selectedRow = matchRows.firstOrNull {
                                    it.index == inspection.selectedIndex
                                }
                                val selectedBounds = selectedRow?.takeIf {
                                    inspection.isActionCurrent(it)
                                }?.let { row ->
                                    occurrenceBounds(row.occurrence, Highlights(true, outlines))
                                }
                                selectedBounds?.let { bounds ->
                                    drawSelectedOccurrenceMarker(bounds)
                                }
                                if (liveAreaIsDisplayed) {
                                    val area = checkNotNull(liveArea.area)
                                    drawRect(
                                        color = Color.White,
                                        topLeft = Offset(
                                            area.left * size.width,
                                            area.top * size.height,
                                        ),
                                        size = Size(
                                            (area.right - area.left) * size.width,
                                            (area.bottom - area.top) * size.height,
                                        ),
                                        style = Stroke(
                                            width = 3.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(
                                                floatArrayOf(10.dp.toPx(), 6.dp.toPx()),
                                            ),
                                        ),
                                    )
                                }
                            }
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
                                Text(
                                    "Scan area",
                                    modifier = Modifier.align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    color = Color.White,
                                )
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
                            Column(
                                Modifier.fillMaxWidth().padding(
                                    horizontal = FinderSpacing.large,
                                    vertical = FinderSpacing.extraSmall,
                                ),
                                verticalArrangement = Arrangement.spacedBy(
                                    FinderSpacing.extraSmall,
                                ),
                            ) {
                                Text(
                                    "Editing scan area",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(
                                        FinderSpacing.small,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(
                                        FinderSpacing.small,
                                    ),
                                ) {
                                    FinderPrimaryAction(
                                        label = "Apply",
                                        enabled = true,
                                        onClick = {
                                            val latestPhase = importedOwner.state.value.phase
                                            val latestFreeze = frozenOwner.state.value
                                            liveArea.applyEditor(areaEditorToken) {
                                                latestFreeze.mode == FreezeMode.Live &&
                                                    importedOwner.state.value.phase ==
                                                        latestPhase &&
                                                    isContentVisible() &&
                                                    liveSearchAreaAvailable() &&
                                                    latestPhase.allowsLiveCamera
                                            }
                                        },
                                    )
                                    FinderSecondaryAction("Cancel", {
                                        liveArea.cancelEditor(areaEditorToken)
                                    })
                                    FinderSecondaryAction("Full frame", {
                                        liveArea.updateEditor(areaEditorToken, null)
                                    })
                                    FinderSecondaryAction(
                                        "Precise",
                                        liveArea.precisionAction(areaEditorToken) {
                                            val phase = importedOwner.state.value.phase
                                            frozenOwner.state.value.mode == FreezeMode.Live &&
                                                phase.allowsLiveCamera &&
                                                isContentVisible() &&
                                                liveSearchAreaAvailable()
                                        },
                                    )
                                }
                            }
                        }
                        FinderLayoutMode.ListManager -> {
                            FinderCaptureControls(
                                captureLabel = captureLabel,
                                captureEnabled = captureEnabled,
                                onCapture = onCapture,
                                pendingCaptureLabel = pendingCaptureLabel,
                                onCancelCapture = frozenOwner::cancel,
                                secondaryActionLabel = directSecondaryActionLabel,
                                onSecondaryAction = onSecondaryAction,
                            )
                        }
                        FinderLayoutMode.StillInspection -> {
                            Column(
                                Modifier.fillMaxWidth().padding(
                                    horizontal = FinderSpacing.large,
                                    vertical = FinderSpacing.extraSmall,
                                ),
                                verticalArrangement = Arrangement.spacedBy(
                                    FinderSpacing.extraSmall,
                                ),
                            ) {
                                shareOwner.state.value.message?.let { message ->
                                    ContextMessage(message = message)
                                }
                                val focusManager = LocalFocusManager.current
                                val keyboardController = LocalSoftwareKeyboardController.current
                                StillInspectionControls(
                                    matchCount = matchRows.size,
                                    showMatchesAction = false,
                                    zoom = inspection.transform.zoom,
                                    canZoom = shouldShowViews && inspection.fit?.isValid == true,
                                    onZoomIn = { inspection.zoomBy(1.25f) },
                                    onZoomOut = { inspection.zoomBy(0.8f) },
                                    onReset = inspection::resetZoom,
                                    onMatches = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        copySession.updateDialogOpen(true)
                                    },
                                )
                                val selectedRow = inspection.selectedNavigationRow
                                if (inspection.selectedPosition > 0 && selectedRow != null) {
                                    val canLocate = shouldShowViews &&
                                        resultsAvailable &&
                                        inspection.isActionCurrent(selectedRow)
                                    if (!canLocate) {
                                        ContextMessage(
                                            message = stringResource(
                                                Res.string.search_results_locate_hint,
                                            ),
                                        )
                                    }
                                    StillMatchNavigationControls(
                                        position = inspection.selectedPosition,
                                        count = inspection.effectiveRows.size,
                                        matchedTerm = selectedRow.termLabel,
                                        matchedText = selectedRow.matchedText,
                                        canLocate = canLocate,
                                        onPrevious = { inspection.locateAdjacent(-1) },
                                        onNext = { inspection.locateAdjacent(1) },
                                        onCopy = {
                                            inspection.copySelected(
                                                StillMatchCopyPayload.MatchedText,
                                            )
                                        },
                                    )
                                }
                                copySession.message?.let { message ->
                                    ContextMessage(message = message)
                                }
                            }
                        }
                        FinderLayoutMode.Live -> Unit
                    }
                    if (layoutMode.showsDock) {
                        val dockFocusManager = LocalFocusManager.current
                        val dockKeyboardController = LocalSoftwareKeyboardController.current
                        FinderLiveDock(
                            query = query,
                            onQueryChange = onManualQueryChange,
                            activeListName = selectedList?.name,
                            activeListTermCount = selectedList?.terms?.size ?: 0,
                            matchMode = activeSearch.matchMode,
                            searchListsLabel = searchListsLabel,
                            onSearchLists = listManager::toggle,
                            onTerms = onShowTerms,
                            onUseManual = onUseManualSearch,
                            onResults = { searchResultsDialogOpen = true },
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
                            saving = sessionState.saving,
                            captureLabel = captureLabel,
                            captureEnabled = captureEnabled,
                            onCapture = onCapture,
                            pendingCaptureLabel = pendingCaptureLabel,
                            onCancelCapture = frozenOwner::cancel,
                            secondaryActionLabel = directSecondaryActionLabel,
                            onSecondaryAction = onSecondaryAction,
                            returnLabel = when (stillSource) {
                                is StillSource.Imported -> "Return to camera"
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
                val selectedDialogRow = inspection.selectedRow
                StillMatchesDialog(
                    rows = matchRows,
                    selectedIndex = inspection.expectedSelectedIndex,
                    copyMessage = copySession.message,
                    onSelect = inspection::selectFromDialog,
                    onDismiss = inspection::dismissMatchesDialog,
                    onCopy = { inspection.copySelected(StillMatchCopyPayload.MatchedText) },
                    onCopyLine = { inspection.copySelected(StillMatchCopyPayload.Line) },
                    onShowOnImage = inspection::showSelectedOnImage,
                    canLocate = shouldShowViews && selectedDialogRow?.let {
                        inspection.isActionCurrent(it)
                    } == true && resultsAvailable,
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
                    ?.name ?: "this list"
                AlertDialog(
                    onDismissRequest = listManager::dismissDelete,
                    title = { Text("Delete search list?") },
                    text = {
                        Text("Delete $name? The saved manual query will be kept.")
                    },
                    confirmButton = {
                        FinderPrimaryAction(
                            label = "Delete",
                            enabled = true,
                            destructive = true,
                            onClick = { listManager.confirmDelete(id) },
                        )
                    },
                    dismissButton = {
                        FinderSecondaryAction(
                            label = "Cancel",
                            onClick = listManager::dismissDelete,
                        )
                    },
                )
            }
            if (resetConfirm) {
                AlertDialog(
                    onDismissRequest = { resetConfirm = false },
                    title = { Text("Reset saved searches?") },
                    text = {
                        Text(
                            "This removes the unreadable saved file and starts with an empty " +
                                "manual query, no lists, and Yellow highlight colour.",
                        )
                    },
                    confirmButton = {
                        FinderPrimaryAction(
                            label = "Reset",
                            enabled = true,
                            destructive = true,
                            onClick = {
                                resetConfirm = false
                                listManager.markContextChanged()
                                session.retry()
                            },
                        )
                    },
                    dismissButton = {
                        FinderSecondaryAction(
                            label = "Cancel",
                            onClick = { resetConfirm = false },
                        )
                    },
                )
            }
            val areaDialogRequest = liveArea.dialogRequest
            if (areaDialogRequest != null && liveSearchAreaAvailable()) {
                val usesDraft = areaDialogRequest.usesDraft
                LiveSearchAreaDialog(
                    area = if (usesDraft) areaEditorDraft else liveArea.area,
                    revision = areaDialogRequest.revision,
                    owner = liveArea.owner,
                    startFullFrame = usesDraft && areaEditorDraft == null,
                    onDismiss = { liveArea.dismissDialog(areaDialogRequest) },
                    onDraftApply = { nextArea ->
                        liveArea.applyDialogArea(areaDialogRequest, nextArea)
                    },
                )
            }
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
    }
}

@Composable
internal fun AppContentColumn(
    scrollState: ScrollState,
    scrollable: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.then(
            if (scrollable) Modifier.verticalScroll(scrollState) else Modifier,
        ),
        content = content,
    )
}
