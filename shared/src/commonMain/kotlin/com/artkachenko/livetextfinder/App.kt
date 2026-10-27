package com.artkachenko.livetextfinder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.camera.CameraPreview
import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.ImageShareStatus
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.ImportedImagePicker
import com.artkachenko.livetextfinder.camera.ImportedImagePreview
import com.artkachenko.livetextfinder.camera.rememberFrozenFrameOwner
import com.artkachenko.livetextfinder.camera.rememberImageShareOwner
import com.artkachenko.livetextfinder.camera.rememberImportedImageOwner
import com.artkachenko.livetextfinder.camera.shareFrozen
import com.artkachenko.livetextfinder.camera.shareImported
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.search.HighlightColor
import com.artkachenko.livetextfinder.search.SearchListEditorState
import com.artkachenko.livetextfinder.search.SearchPresentationOwner
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.drawHighlightOutlines
import com.artkachenko.livetextfinder.search.drawableElementsForSession
import com.artkachenko.livetextfinder.search.importedHighlightsForDraw
import com.artkachenko.livetextfinder.search.inMemorySearchSessionController
import com.artkachenko.livetextfinder.search.projectFrozenHighlights
import com.artkachenko.livetextfinder.search.projectHighlights
import com.artkachenko.livetextfinder.search.projectImportedHighlights
import com.artkachenko.livetextfinder.search.searchRecognizedContent
import com.artkachenko.livetextfinder.search.searchRecognizedText
import com.artkachenko.livetextfinder.utils.MonotonicClock
import kotlinx.coroutines.delay

/** Main live text search screen. */
@Composable
fun App(session: SearchSessionController = remember { inMemorySearchSessionController() }) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            var state by remember { mutableStateOf(OcrUiState()) }
            val importedOwner = rememberImportedImageOwner()
            ImportedImagePicker(importedOwner)
            val importedState = importedOwner.state.value
            val sessionState = session.state.value
            val query = sessionState.persisted.manualQuery
            val selectedList = sessionState.activeList
            val managerOpenState = remember { mutableStateOf(false) }
            var managerOpen by managerOpenState
            var editor by remember { mutableStateOf(SearchListEditorState()) }
            var deleteId by remember { mutableStateOf<String?>(null) }
            var resetConfirm by remember { mutableStateOf(false) }
            var colorDialogOpen by remember { mutableStateOf(false) }
            var helpDialogOpen by remember { mutableStateOf(false) }
            var previewSize by remember { mutableStateOf(IntSize.Zero) }
            val rootSizeState = remember { mutableStateOf(IntSize.Zero) }
            val contentScrollState = rememberScrollState()
            var rootSize by rootSizeState
            var graceTimerRevision by remember { mutableIntStateOf(0) }
            val frozenOwner = rememberFrozenFrameOwner()
            val shareOwner = rememberImageShareOwner()
            val freezeState = frozenOwner.state.value
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
            val activeSearch = sessionState.activeSearch
            val presentation = remember(activeSearch, state, graceTimerRevision) {
                presentationOwner.present(activeSearch, state, MonotonicClock.nowMillis())
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
            AppContentColumn(
                scrollState = contentScrollState,
                scrollable = !shouldShowViews,
                modifier = Modifier.fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .onSizeChanged { rootSize = it },
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {

                    if (shouldShowViews) {
                        Text(
                            "Live Text Finder",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = session::setManualQuery,
                        label = { Text("Find in recognized text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = {
                            managerOpen = !managerOpen
                            editor = editor.close()
                        }) {
                            Text(if (managerOpen) "Close lists" else "Search lists")
                        }
                        if (selectedList != null) {
                            Text(
                                "${selectedList.name} · ${selectedList.terms.size} terms",
                                modifier = Modifier.weight(1f).padding(top = 12.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Text(
                                "Manual search",
                                modifier = Modifier.weight(1f).padding(top = 12.dp),
                                maxLines = 1,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { colorDialogOpen = true },
                            enabled = sessionState.loaded && !sessionState.recoveryRequired,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                "Highlight color: " +
                                    sessionState.persisted.highlightColor.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { helpDialogOpen = true }) {
                            Text("Help")
                        }
                    }
                    if (importedState.phase == ImportedImagePhase.Live ||
                        importedState.phase == ImportedImagePhase.Error
                    ) {
                        when (freezeState.mode) {
                            FreezeMode.Live -> TextButton(
                                onClick = {
                                    frozenOwner.freeze(
                                        cameraReady = state.status == OcrStatus.Scanning ||
                                            state.status == OcrStatus.TextRecognized,
                                    )
                                },
                                enabled = state.status == OcrStatus.Scanning ||
                                    state.status == OcrStatus.TextRecognized,
                            ) {
                                Text("Freeze frame")
                            }
                            FreezeMode.Freezing -> TextButton(onClick = frozenOwner::cancel) {
                                Text("Cancel freeze")
                            }
                            FreezeMode.Frozen -> TextButton(onClick = frozenOwner::resume) {
                                Text("Resume live")
                            }
                        }
                    }
                    when (importedState.phase) {
                        ImportedImagePhase.Unsupported -> Unit
                        ImportedImagePhase.Imported -> {
                            TextButton(onClick = importedOwner::cancel) {
                                Text("Return to camera")
                            }
                        }
                        ImportedImagePhase.Picking,
                        ImportedImagePhase.Reading,
                        ImportedImagePhase.Recognizing -> {
                            TextButton(onClick = importedOwner::cancel) {
                                Text("Cancel import")
                            }
                        }
                        ImportedImagePhase.Live,
                        ImportedImagePhase.Error -> {
                            TextButton(onClick = {
                                if (freezeState.mode == FreezeMode.Freezing) {
                                    frozenOwner.cancel()
                                }
                                frozenOwner.resume()
                                importedOwner.beginPicker()
                            }) {
                                Text("Import image")
                            }
                        }
                    }
                    if (freezeState.error != null) {
                        Text(freezeState.error, color = MaterialTheme.colorScheme.error)
                    }
                    if (sessionState.recoveryRequired) {
                        Text(
                            sessionState.error ?: "Saved search data needs recovery.",
                        )
                        Row {
                            TextButton(onClick = { resetConfirm = true }) {
                                Text("Reset saved data")
                            }
                            Text("Reset clears saved lists, the saved query, and colour to Yellow.")
                        }
                    } else if (sessionState.error != null) {
                        Text(
                            "${sessionState.error}${if (sessionState.saving) " · saving" else ""}",
                        )
                        TextButton(onClick = session::retry) { Text("Retry") }
                    } else if (!sessionState.loaded) {
                        Text("Loading saved searches…")
                    } else if (sessionState.saving) {
                        Text("Saving…")
                    }
                    if (importedState.phase == ImportedImagePhase.Imported) {
                        Text(
                            "Imported matches: ${search.occurrences.size} · " +
                                "outlined elements: ${highlights.outlines.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.heightIn(min = 40.dp, max = 40.dp),
                        )
                    } else if (importedState.phase == ImportedImagePhase.Picking ||
                        importedState.phase == ImportedImagePhase.Reading ||
                        importedState.phase == ImportedImagePhase.Recognizing
                    ) {
                        Text(
                            when (importedState.phase) {
                                ImportedImagePhase.Picking -> "Choosing image…"
                                ImportedImagePhase.Reading -> "Reading image…"
                                else -> "Recognizing text…"
                            },
                        )
                    } else if (importedState.phase == ImportedImagePhase.Error) {
                        Text(
                            importedState.message.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (shouldShowViews) {
                        Text(
                            when {
                                freezeState.mode == FreezeMode.Frozen -> {
                                    "Frozen matches: ${search.occurrences.size} · " +
                                        "outlined elements: ${highlights.outlines.size}"
                                }
                                search.snapshot == null && state.status == OcrStatus.Scanning -> {
                                    "Waiting for fresh recognized text"
                                }
                                search.snapshot == null -> "Search paused · see camera status"
                                search.query.isEmpty() -> "Enter text to search"
                                search.occurrences.isEmpty() &&
                                    presentation.outlinedElements.isNotEmpty() ->
                                    "No current matches · retaining " +
                                        "${presentation.outlinedElements.size} outlines briefly"
                                search.occurrences.isEmpty() -> "No matches in recognized text"
                                !highlights.ready -> {
                                    "Recognized matches: ${search.occurrences.size} · " +
                                        "Highlights waiting for preview"
                                }
                                presentation.occurrences.any {
                                    it.firstMissingAtMillis != null
                                } -> {
                                    "Recognized matches: ${search.occurrences.size} · " +
                                        "outlined elements: " +
                                        "${presentation.outlinedElements.size} " +
                                        "(some briefly retained)"
                                }
                                else ->
                                    "Recognized matches: ${search.occurrences.size} · " +
                                        "outlined elements: " +
                                        presentation.outlinedElements.size
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.heightIn(min = 40.dp, max = 40.dp),
                        )
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
                                    ) "Preparing image…" else "Share image",
                                )
                            }
                            if (shareOwner.state.value.status == ImageShareStatus.Preparing) {
                                TextButton(onClick = shareOwner::cancel) { Text("Cancel") }
                            }
                        }
                        shareOwner.state.value.message?.let { message -> Text(message) }
                    }
                }
                if (managerOpen) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Search lists", style = MaterialTheme.typography.titleMedium)
                        Button(
                            onClick = { editor = editor.startNew() },
                            enabled = sessionState.loaded && !sessionState.recoveryRequired,
                        ) {
                            Text("New list")
                        }
                        sessionState.persisted.lists.forEach { list ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = { session.selectList(list.id) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        if (sessionState.persisted.activeListId == list.id) {
                                            "✓ ${list.name}"
                                        } else {
                                            list.name
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                TextButton(onClick = { editor = editor.edit(list) }) {
                                    Text("Edit")
                                }
                                TextButton(onClick = { deleteId = list.id }) {
                                    Text("Delete")
                                }
                            }
                        }
                        if (editor.isOpen) {
                            OutlinedTextField(
                                value = editor.name,
                                onValueChange = { editor = editor.copy(name = it) },
                                label = { Text("List name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = editor.terms,
                                onValueChange = { editor = editor.copy(terms = it) },
                                label = { Text("Terms or phrases · one per line") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp),
                            )
                            editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
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
                                    enabled = sessionState.loaded && !sessionState.recoveryRequired,
                                ) {
                                    Text("Save list")
                                }
                                TextButton(onClick = { editor = editor.close() }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
                if (shouldShowViews) {
                    Box(
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
                            val outlines = if (
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
                                    ).map { it.outline }
                                } else {
                                    emptyList()
                                }
                            }
                            drawHighlightOutlines(
                                outlines,
                                session.state.value.persisted.highlightColor,
                            )
                        }
                    }
                }
                if (shouldShowViews) {
                    Column(
                        Modifier.fillMaxWidth()
                            .height(88.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "OCR: ${state.status.name} · Latin script",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("Recognized lines: ${state.snapshot?.lines?.size ?: 0}", maxLines = 1)
                        state.snapshot?.let { snapshot ->
                            Text(
                                "${snapshot.frame.uprightWidth} × " +
                                    "${snapshot.frame.uprightHeight} · rotation " +
                                    "${snapshot.frame.rotationDegrees}°",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                            if (snapshot.text.isNotBlank()) {
                                Text(
                                    snapshot.text.take(240),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
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
            if (colorDialogOpen) {
                AlertDialog(
                    onDismissRequest = { colorDialogOpen = false },
                    title = { Text("Highlight color") },
                    text = {
                        Column(
                            Modifier.heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState())
                                .selectableGroup(),
                        ) {
                            HighlightColor.entries.forEach { color ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().selectable(
                                        selected = sessionState.persisted.highlightColor == color,
                                        enabled = sessionState.loaded &&
                                            !sessionState.recoveryRequired,
                                        role = Role.RadioButton,
                                        onClick = { session.setHighlightColor(color) },
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = sessionState.persisted.highlightColor == color,
                                        onClick = null,
                                        enabled = sessionState.loaded &&
                                            !sessionState.recoveryRequired,
                                    )
                                    Box(
                                        Modifier.padding(end = 12.dp).size(24.dp)
                                            .background(color.composeColor),
                                    )
                                    Text(color.displayName)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { colorDialogOpen = false }) { Text("Done") }
                    },
                )
            }
            if (helpDialogOpen) {
                UsageHelpDialog(onDismissRequest = { helpDialogOpen = false })
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
