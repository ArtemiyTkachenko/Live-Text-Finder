package com.artkachenko.livetextfinder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.camera.CameraPreview
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.search.SearchListEditorState
import com.artkachenko.livetextfinder.search.SearchPresentationOwner
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.drawableElementsForSession
import com.artkachenko.livetextfinder.search.inMemorySearchSessionController
import com.artkachenko.livetextfinder.search.projectHighlights
import com.artkachenko.livetextfinder.utils.MonotonicClock
import com.artkachenko.livetextfinder.utils.isKeyboardVisible
import kotlinx.coroutines.delay

/** Main live text search screen. */
@Composable
fun App(session: SearchSessionController = remember { inMemorySearchSessionController() }) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            var state by remember { mutableStateOf(OcrUiState()) }
            val sessionState = session.state.value
            val query = sessionState.persisted.manualQuery
            val selectedList = sessionState.activeList
            var managerOpen by remember { mutableStateOf(false) }
            var editor by remember { mutableStateOf(SearchListEditorState()) }
            var deleteId by remember { mutableStateOf<String?>(null) }
            var resetConfirm by remember { mutableStateOf(false) }
            var previewSize by remember { mutableStateOf(IntSize.Zero) }
            var rootSize by remember { mutableStateOf(IntSize.Zero) }
            var graceTimerRevision by remember { mutableIntStateOf(0) }
            val presentationOwner = remember { SearchPresentationOwner() }
            val activeSearch = sessionState.activeSearch
            val presentation = remember(activeSearch, state, graceTimerRevision) {
                presentationOwner.present(activeSearch, state, MonotonicClock.nowMillis())
            }
            val search = presentation.search
            LaunchedEffect(presentation.expiresAtMillis) {
                val deadline = presentation.expiresAtMillis ?: return@LaunchedEffect
                val wait = deadline - MonotonicClock.nowMillis()
                if (wait > 0) delay(wait)
                graceTimerRevision++
            }
            val highlights = remember(search, state.projection, previewSize) {
                projectHighlights(search, state.projection, previewSize.width, previewSize.height)
            }
            val density = LocalDensity.current
            val compactLayout =
                (rootSize != IntSize.Zero && rootSize.height / density.density < 500f) ||
                density.fontScale >= 1.3f
            val keyboardVisible = isKeyboardVisible()

            // This is the only IME inset owner. The weighted preview receives the remaining
            // height directly, so OCR status changes do not recalculate its available space.
            Column(
                Modifier.fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .onSizeChanged { rootSize = it },
            ) {
                val shouldShowViews = !compactLayout && !keyboardVisible && !managerOpen
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            managerOpen = !managerOpen
                            editor = editor.close()
                        }) {
                            Text(if (managerOpen) "Close lists" else "Search lists")
                        }
                        if (selectedList != null) {
                            Text(
                                "${selectedList.name} · ${selectedList.terms.size} terms",
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        } else {
                            Text("Manual search", modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    if (sessionState.recoveryRequired) {
                        Text(
                            sessionState.error ?: "Saved search data needs recovery.",
                        )
                        Row {
                            TextButton(onClick = { resetConfirm = true }) {
                                Text("Reset saved data")
                            }
                            Text("Reset clears saved lists and the saved query.")
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
                    if (shouldShowViews) {
                        Text(
                            when {
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
                }
                if (managerOpen) {
                    Column(
                        Modifier.fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
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
                        CameraPreview(Modifier.fillMaxSize()) { state = it }
                        Canvas(Modifier.matchParentSize()) {
                            val outlines = drawableElementsForSession(
                                session,
                                activeSearch,
                                presentation,
                                state,
                                MonotonicClock.nowMillis(),
                                size.width,
                                size.height,
                            )
                            clipRect {
                                outlines.forEach { presented ->
                                    val outline = presented.outline
                                    val path = Path().apply {
                                        moveTo(outline.points.first().x, outline.points.first().y)
                                        outline.points.drop(1).forEach { lineTo(it.x, it.y) }
                                        close()
                                    }
                                    drawPath(path, Color.Black, style = Stroke(6.dp.toPx()))
                                    drawPath(path, Color.Yellow, style = Stroke(3.dp.toPx()))
                                }
                            }
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
                                "manual query and no lists.",
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
        }
    }
}
