package com.artkachenko.livetextfinder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.camera.CameraPreview
import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.FrozenFrameFit
import com.artkachenko.livetextfinder.camera.ImageShareStatus
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.ImportedImagePicker
import com.artkachenko.livetextfinder.camera.ImportedImagePreview
import com.artkachenko.livetextfinder.camera.LiveSearchAreaLifecycle
import com.artkachenko.livetextfinder.camera.StillViewTransform
import com.artkachenko.livetextfinder.camera.liveSearchAreaAvailable
import com.artkachenko.livetextfinder.camera.plainTextClipEntry
import com.artkachenko.livetextfinder.camera.rememberFrozenFrameOwner
import com.artkachenko.livetextfinder.camera.rememberImageShareOwner
import com.artkachenko.livetextfinder.camera.rememberImportedImageOwner
import com.artkachenko.livetextfinder.camera.shareFrozen
import com.artkachenko.livetextfinder.camera.shareImported
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.search.LiveSearchAreaOwner
import com.artkachenko.livetextfinder.search.NormalizedSearchArea
import com.artkachenko.livetextfinder.search.PreviewPoint
import com.artkachenko.livetextfinder.search.SearchHighlightPalette
import com.artkachenko.livetextfinder.search.SearchListEditorState
import com.artkachenko.livetextfinder.search.SearchPresentationOwner
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.drawHighlightOutlines
import com.artkachenko.livetextfinder.search.drawableElementsForSession
import com.artkachenko.livetextfinder.search.importedHighlightsForDraw
import com.artkachenko.livetextfinder.search.inMemorySearchSessionController
import com.artkachenko.livetextfinder.search.isPresentationCurrentAreaRevision
import com.artkachenko.livetextfinder.search.projectFrozenHighlights
import com.artkachenko.livetextfinder.search.projectHighlights
import com.artkachenko.livetextfinder.search.projectImportedHighlights
import com.artkachenko.livetextfinder.search.searchRecognizedContent
import com.artkachenko.livetextfinder.search.searchRecognizedText
import com.artkachenko.livetextfinder.search.searchTermSummaries
import com.artkachenko.livetextfinder.search.stillMatchRows
import com.artkachenko.livetextfinder.ui.ContextMessage
import com.artkachenko.livetextfinder.ui.FinderOptionsSheet
import com.artkachenko.livetextfinder.ui.FinderSearchControls
import com.artkachenko.livetextfinder.ui.FinderSearchLists
import com.artkachenko.livetextfinder.ui.FinderSpacing
import com.artkachenko.livetextfinder.ui.HighlightColorDialog
import com.artkachenko.livetextfinder.ui.LiveTextFinderTheme
import com.artkachenko.livetextfinder.ui.SearchTermResultsDialog
import com.artkachenko.livetextfinder.ui.StillInspectionControls
import com.artkachenko.livetextfinder.ui.StillInspectionViewport
import com.artkachenko.livetextfinder.ui.StillMatchesDialog
import com.artkachenko.livetextfinder.ui.rememberStillViewTransform
import com.artkachenko.livetextfinder.utils.MonotonicClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.finder_options_highlight
import livetextfinder.shared.generated.resources.search_results
import livetextfinder.shared.generated.resources.status_cancel
import livetextfinder.shared.generated.resources.status_share_image
import livetextfinder.shared.generated.resources.status_share_preparing
import livetextfinder.shared.generated.resources.still_match_copied
import livetextfinder.shared.generated.resources.still_match_copy_failed
import org.jetbrains.compose.resources.stringResource

/** Main live text search screen. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(session: SearchSessionController = remember { inMemorySearchSessionController() }) {
    LiveTextFinderTheme {
        Surface(Modifier.fillMaxSize()) {
            var state by remember { mutableStateOf(OcrUiState()) }
            val importedOwner = rememberImportedImageOwner()
            ImportedImagePicker(importedOwner)
            val importedState = importedOwner.state.value
            val sessionState = session.state.value
            val activeSearch = sessionState.activeSearch
            val query = sessionState.persisted.manualQuery
            val selectedList = sessionState.activeList
            val managerOpenState = remember { mutableStateOf(false) }
            var managerOpen by managerOpenState
            var editor by remember { mutableStateOf(SearchListEditorState()) }
            var deleteId by remember { mutableStateOf<String?>(null) }
            var resetConfirm by remember { mutableStateOf(false) }
            var colorDialogOpen by remember { mutableStateOf(false) }
            var areaDialogOpen by remember { mutableStateOf(false) }
            val areaOwner = remember { LiveSearchAreaOwner() }
            var areaState by remember { mutableStateOf<NormalizedSearchArea?>(null) }
            var areaRevision by remember { mutableLongStateOf(areaOwner.revision) }
            var helpDialogOpen by remember { mutableStateOf(false) }
            var searchResultsDialogOpen by remember(activeSearch.identity) { mutableStateOf(false) }
            var optionsOpen by remember { mutableStateOf(false) }
            var previewSize by remember { mutableStateOf(IntSize.Zero) }
            val rootSizeState = remember { mutableStateOf(IntSize.Zero) }
            val contentScrollState = rememberScrollState()
            var rootSize by rootSizeState
            var graceTimerRevision by remember { mutableIntStateOf(0) }
            val frozenOwner = rememberFrozenFrameOwner()
            val shareOwner = rememberImageShareOwner()
            val freezeState = frozenOwner.state.value
            fun resetLiveArea() {
                areaOwner.reset()
                areaState = null
                areaRevision = areaOwner.revision
                areaDialogOpen = false
            }
            LiveSearchAreaLifecycle(::resetLiveArea)
            val density = LocalDensity.current
            val imeInsets = WindowInsets.ime
            val isContentVisible = {
                val currentRootSize = rootSizeState.value
                val isCompact =
                    (currentRootSize != IntSize.Zero &&
                        currentRootSize.height / density.density < 500f) ||
                        density.fontScale >= 1.3f
                !managerOpenState.value && !isCompact && imeInsets.getBottom(density) == 0
            }
            val presentationOwner = remember { SearchPresentationOwner() }
            val presentation = remember(
                activeSearch,
                state,
                graceTimerRevision,
                areaState,
                areaRevision,
                previewSize,
            ) {
                presentationOwner.present(
                    activeSearch,
                    state,
                    MonotonicClock.nowMillis(),
                    areaState,
                    areaRevision,
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
            val sourceIdentity = when {
                importedState.phase == ImportedImagePhase.Imported ->
                    "import:${importedState.snapshot?.sourceIdentity}"
                freezeState.mode == FreezeMode.Frozen ->
                    "frozen:${freezeState.frame?.identity}"
                else -> null
            }
            val inspectionIdentity = when {
                importedState.phase == ImportedImagePhase.Imported -> sourceIdentity
                freezeState.mode == FreezeMode.Frozen ->
                    "${sourceIdentity}:${freezeState.frame?.requestId}"
                else -> null
            }
            val stillSnapshot = when {
                importedState.phase == ImportedImagePhase.Imported -> importedState.snapshot
                freezeState.mode == FreezeMode.Frozen -> freezeState.frame?.snapshot
                else -> null
            }
            val stillFit = stillSnapshot?.let { snapshot ->
                if (snapshot is com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot) {
                    FrozenFrameFit(
                        snapshot.width,
                        snapshot.height,
                        0,
                        previewSize.width.toFloat(),
                        previewSize.height.toFloat(),
                    )
                } else {
                    val frame = (snapshot as com.artkachenko.livetextfinder.ocr.OcrSnapshot).frame
                    FrozenFrameFit(
                        frame.bufferWidth,
                        frame.bufferHeight,
                        frame.rotationDegrees,
                        previewSize.width.toFloat(),
                        previewSize.height.toFloat(),
                    )
                }
            }
            var stillTransform by rememberStillViewTransform(inspectionIdentity, previewSize)
            val matchRows = remember(
                search,
                inspectionIdentity,
                sessionState.persisted.highlightColor,
            ) {
                if (inspectionIdentity != null) {
                    stillMatchRows(search, sessionState.persisted.highlightColor)
                } else {
                    emptyList()
                }
            }
            val termSummaries = remember(search, sessionState.persisted.highlightColor) {
                searchTermSummaries(search, sessionState.persisted.highlightColor)
            }
            var matchesDialogOpen by remember(inspectionIdentity, activeSearch.identity) {
                mutableStateOf(false)
            }
            var selectedMatch by remember(inspectionIdentity, activeSearch.identity) {
                mutableStateOf<Int?>(null)
            }
            var copyMessage by remember(inspectionIdentity, activeSearch.identity) {
                mutableStateOf<String?>(null)
            }
            val clipboard = LocalClipboard.current
            val coroutineScope = rememberCoroutineScope()
            LaunchedEffect(sourceIdentity, activeSearch.identity, freezeState.mode) {
                if (sourceIdentity == null || freezeState.mode != FreezeMode.Frozen &&
                    importedState.phase != ImportedImagePhase.Imported
                ) {
                    matchesDialogOpen = false
                    selectedMatch = null
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
            val hasLiveCaptureControls = importedState.phase == ImportedImagePhase.Live ||
                importedState.phase == ImportedImagePhase.Error
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
                if (areaState == null) "Live area: Full frame" else "Live area: Custom"
            } else {
                null
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
            AppContentColumn(
                scrollState = contentScrollState,
                scrollable = !shouldShowViews,
                modifier = Modifier.fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .onSizeChanged { rootSize = it },
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(
                        horizontal = FinderSpacing.large,
                        vertical = FinderSpacing.extraSmall,
                    ),
                    verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
                ) {
                    FinderSearchControls(
                        showTitle = shouldShowViews,
                        query = query,
                        onQueryChange = session::setManualQuery,
                        searchListsLabel = if (managerOpen) "Close lists" else "Search lists",
                        onSearchLists = {
                            managerOpen = !managerOpen
                            editor = editor.close()
                        },
                        searchContext = selectedList?.let {
                            "${it.name} · ${it.terms.size} terms"
                        } ?: "Manual search",
                        onOptions = { optionsOpen = true },
                        captureLabel = captureLabel,
                        captureEnabled = freezeState.mode != FreezeMode.Live ||
                            state.status == OcrStatus.Scanning ||
                            state.status == OcrStatus.TextRecognized,
                        onCapture = {
                            when (freezeState.mode) {
                                FreezeMode.Live -> {
                                    resetLiveArea()
                                    frozenOwner.freeze(
                                        cameraReady = state.status == OcrStatus.Scanning ||
                                            state.status == OcrStatus.TextRecognized,
                                    )
                                }
                                FreezeMode.Frozen -> {
                                    resetLiveArea()
                                    frozenOwner.resume()
                                }
                                FreezeMode.Freezing -> Unit
                            }
                        },
                        pendingCaptureLabel = pendingCaptureLabel,
                        onCancelCapture = frozenOwner::cancel,
                        secondaryActionLabel = directSecondaryActionLabel,
                        onSecondaryAction = {
                            when (importedState.phase) {
                                ImportedImagePhase.Unsupported -> Unit
                                ImportedImagePhase.Imported -> {
                                    resetLiveArea()
                                    importedOwner.cancel()
                                }
                                ImportedImagePhase.Picking,
                                ImportedImagePhase.Reading,
                                ImportedImagePhase.Recognizing -> importedOwner.cancel()
                                ImportedImagePhase.Live,
                                ImportedImagePhase.Error -> Unit
                            }
                        },
                    )
                    FinderStatusRegion(
                        state = state,
                        importedState = importedState,
                        freezeState = freezeState,
                        sessionState = sessionState,
                        search = search,
                        presentation = presentation,
                        highlights = highlights,
                        areaSelected = areaState != null,
                        shouldShowViews = shouldShowViews,
                        onRetrySavedData = session::retry,
                        onRequestReset = { resetConfirm = true },
                    )
                    if (activeSearch.terms.isNotEmpty()) {
                        TextButton(onClick = { searchResultsDialogOpen = true }) {
                            Text(stringResource(Res.string.search_results, search.occurrences.size))
                        }
                    }
                    if (sourceIdentity != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    if (importedState.phase == ImportedImagePhase.Imported) {
                                        shareOwner.shareImported(
                                            importedOwner,
                                            search,
                                            currentSourceIdentity = {
                                                importedOwner.state.value.snapshot?.let {
                                                    "import:${it.sourceIdentity}"
                                                }
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
                                                frozenOwner.state.value.frame?.let {
                                                    "frozen:${it.identity}"
                                                }
                                            },
                                            currentSearchIdentity = {
                                                session.state.value.activeSearch.identity
                                            },
                                            currentHighlightColor = {
                                                session.state.value.persisted.highlightColor
                                            },
                                        )
                                    }
                                },
                                enabled = shareOwner.state.value.status !=
                                    ImageShareStatus.Preparing,
                            ) {
                                Text(
                                    if (shareOwner.state.value.status ==
                                        ImageShareStatus.Preparing
                                    ) {
                                        stringResource(Res.string.status_share_preparing)
                                    } else {
                                        stringResource(Res.string.status_share_image)
                                    },
                                )
                            }
                            if (shareOwner.state.value.status == ImageShareStatus.Preparing) {
                                TextButton(onClick = shareOwner::cancel) {
                                    Text(stringResource(Res.string.status_cancel))
                                }
                            }
                        }
                        shareOwner.state.value.message?.let { message ->
                            ContextMessage(message = message)
                        }
                        val focusManager = LocalFocusManager.current
                        val keyboardController = LocalSoftwareKeyboardController.current
                        StillInspectionControls(
                            matchCount = matchRows.size,
                            zoom = stillTransform.zoom,
                            canZoom = shouldShowViews && stillFit?.isValid == true,
                            onZoomIn = {
                                stillTransform = stillTransform.zoomBy(
                                    1.25f,
                                    PreviewPoint(
                                        previewSize.width / 2f,
                                        previewSize.height / 2f,
                                    ),
                                    previewSize.width.toFloat(),
                                    previewSize.height.toFloat(),
                                    checkNotNull(stillFit),
                                )
                            },
                            onZoomOut = {
                                stillTransform = stillTransform.zoomBy(
                                    0.8f,
                                    PreviewPoint(
                                        previewSize.width / 2f,
                                        previewSize.height / 2f,
                                    ),
                                    previewSize.width.toFloat(),
                                    previewSize.height.toFloat(),
                                    checkNotNull(stillFit),
                                )
                            },
                            onReset = { stillTransform = StillViewTransform() },
                            onMatches = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                selectedMatch = null
                                matchesDialogOpen = true
                            },
                        )
                        copyMessage?.let { message -> ContextMessage(message = message) }
                    }
                }
                if (managerOpen) {
                    FinderSearchLists(
                        lists = sessionState.persisted.lists,
                        activeListId = sessionState.persisted.activeListId,
                        editor = editor,
                        canSave = sessionState.loaded && !sessionState.recoveryRequired,
                        onNew = { editor = editor.startNew() },
                        onSelect = session::selectList,
                        onEdit = { list -> editor = editor.edit(list) },
                        onDelete = { list -> deleteId = list.id },
                        onNameChange = { editor = editor.copy(name = it) },
                        onTermsChange = { editor = editor.copy(terms = it) },
                        onSave = {
                            val error = session.saveList(
                                editor.editingId,
                                editor.name,
                                editor.terms,
                            )
                            editor = if (error == null) {
                                editor.close()
                            } else {
                                editor.copy(error = error)
                            }
                        },
                        onCancel = { editor = editor.close() },
                    )
                }
                if (shouldShowViews) {
                    StillInspectionViewport(
                        sourceIdentity = inspectionIdentity,
                        fit = stillFit,
                        viewportSize = previewSize,
                        transform = stillTransform,
                        onTransformChange = { stillTransform = it },
                        modifier = Modifier.weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black)
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
                                isContentVisible,
                            ) { state = it }
                        }
                        Canvas(Modifier.matchParentSize()) {
                            val currentFreezeState = frozenOwner.state.value
                            val currentSearchIdentity = session.state.value.activeSearch.identity
                            val currentImportState = importedOwner.state.value
                            val areaIsCurrent =
                                isPresentationCurrentAreaRevision(
                                    presentation,
                                    areaOwner.revision,
                                ) &&
                                areaRevision == presentation.areaRevision
                            val liveAreaIsDisplayed = areaIsCurrent && areaState != null &&
                                importedState.phase != ImportedImagePhase.Imported &&
                                importedState.phase != ImportedImagePhase.Picking &&
                                importedState.phase != ImportedImagePhase.Reading &&
                                importedState.phase != ImportedImagePhase.Recognizing &&
                                currentFreezeState.mode == FreezeMode.Live &&
                                currentImportState.phase != ImportedImagePhase.Imported &&
                                currentImportState.phase != ImportedImagePhase.Picking &&
                                currentImportState.phase != ImportedImagePhase.Reading &&
                                currentImportState.phase != ImportedImagePhase.Recognizing &&
                                areaOwner.area == areaState &&
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
                            } else if (
                                importedState.phase == ImportedImagePhase.Picking ||
                                importedState.phase == ImportedImagePhase.Reading ||
                                importedState.phase == ImportedImagePhase.Recognizing
                            ) {
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
                            if (liveAreaIsDisplayed) {
                                val area = checkNotNull(areaState)
                                clipRect(
                                    left = area.left * size.width,
                                    top = area.top * size.height,
                                    right = area.right * size.width,
                                    bottom = area.bottom * size.height,
                                ) {
                                    drawHighlightOutlines(
                                        outlines,
                                        session.state.value.persisted.highlightColor,
                                        SearchHighlightPalette.colorsByTerm(
                                            activeSearch,
                                            session.state.value.persisted.highlightColor,
                                        ),
                                    )
                                }
                            } else {
                                drawHighlightOutlines(
                                    outlines,
                                    session.state.value.persisted.highlightColor,
                                    SearchHighlightPalette.colorsByTerm(
                                        activeSearch,
                                        session.state.value.persisted.highlightColor,
                                    ),
                                )
                            }
                            if (liveAreaIsDisplayed) {
                                val area = checkNotNull(areaState)
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
                        if (areaState != null &&
                            areaOwner.revision == presentation.areaRevision &&
                            areaOwner.area == areaState &&
                            importedState.phase != ImportedImagePhase.Imported &&
                            importedOwner.state.value.phase != ImportedImagePhase.Imported &&
                            frozenOwner.state.value.mode == FreezeMode.Live &&
                            isContentVisible()
                        ) {
                            Text(
                                "Live area",
                                modifier = Modifier.align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                color = Color.White,
                            )
                        }
                    }
                }
            }
            if (matchesDialogOpen && sourceIdentity != null) {
                val copyFailedMessage = stringResource(Res.string.still_match_copy_failed)
                val copiedMessage = stringResource(Res.string.still_match_copied)
                StillMatchesDialog(
                    rows = matchRows,
                    selectedIndex = selectedMatch,
                    copyMessage = copyMessage,
                    onSelect = { selectedMatch = it },
                    onDismiss = { matchesDialogOpen = false },
                    onCopy = {
                        val selectedIndex = selectedMatch
                        val row = matchRows.firstOrNull { it.index == selectedIndex }
                        val expectedOwnerToken = if (
                            importedOwner.state.value.phase == ImportedImagePhase.Imported
                        ) {
                            importedOwner.state.value.snapshot
                        } else {
                            frozenOwner.state.value.frame
                        }
                        if (selectedIndex == null || row == null || expectedOwnerToken == null) {
                            copyMessage = copyFailedMessage
                        } else {
                            coroutineScope.launch {
                                try {
                                    val outcome = StillMatchCopyCoordinator(
                                        importedOwner = importedOwner,
                                        frozenOwner = frozenOwner,
                                        session = session,
                                        selectedIndex = { selectedMatch },
                                        writeText = { text ->
                                            clipboard.setClipEntry(plainTextClipEntry(text))
                                        },
                                    ).copy(
                                        row,
                                        search,
                                        sourceIdentity,
                                        activeSearch.identity,
                                        selectedIndex,
                                        expectedOwnerToken,
                                    )
                                    when (outcome) {
                                        StillMatchCopyOutcome.Copied -> {
                                            copyMessage = copiedMessage
                                            matchesDialogOpen = false
                                        }
                                        StillMatchCopyOutcome.Stale,
                                        StillMatchCopyOutcome.Failed -> {
                                            copyMessage = copyFailedMessage
                                        }
                                    }
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    copyMessage = copyFailedMessage
                                }
                            }
                        }
                    },
                )
            }
            if (searchResultsDialogOpen) {
                SearchTermResultsDialog(
                    terms = termSummaries,
                    onDismiss = { searchResultsDialogOpen = false },
                )
            }
            deleteId?.let { id ->
                val name = sessionState.persisted.lists.firstOrNull { list -> list.id == id }
                    ?.name ?: "this list"
                AlertDialog(
                    onDismissRequest = { deleteId = null },
                    title = { Text("Delete search list?") },
                    text = {
                        Text("Delete $name? The saved manual query will be kept.")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                session.deleteList(id)
                                if (editor.editingId == id) {
                                    editor = editor.close()
                                }
                                deleteId = null
                            },
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteId = null }) { Text("Cancel") }
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
                        TextButton(onClick = {
                            resetConfirm = false
                            session.retry()
                        }) {
                            Text("Reset")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { resetConfirm = false }) { Text("Cancel") }
                    },
                )
            }
            if (areaDialogOpen && liveSearchAreaAvailable()) {
                LiveSearchAreaDialog(
                    area = areaState,
                    revision = areaRevision,
                    owner = areaOwner,
                    onDismiss = {
                        areaState = areaOwner.area
                        areaRevision = areaOwner.revision
                        areaDialogOpen = false
                    },
                )
            }
            if (colorDialogOpen) {
                HighlightColorDialog(
                    selectedColor = sessionState.persisted.highlightColor,
                    enabled = sessionState.loaded && !sessionState.recoveryRequired,
                    onSelectColor = session::setHighlightColor,
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
                        sessionState.persisted.highlightColor.displayName,
                    ),
                    highlightEnabled = sessionState.loaded && !sessionState.recoveryRequired,
                    liveAreaLabel = liveAreaLabel,
                    showImport = importedState.phase == ImportedImagePhase.Live ||
                        importedState.phase == ImportedImagePhase.Error,
                    onHighlight = {
                        optionsOpen = false
                        colorDialogOpen = true
                    },
                    onLiveArea = {
                        optionsOpen = false
                        areaDialogOpen = true
                    },
                    onImport = {
                        optionsOpen = false
                        resetLiveArea()
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
