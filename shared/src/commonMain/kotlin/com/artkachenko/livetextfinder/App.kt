package com.artkachenko.livetextfinder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.camera.CameraPreview
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.search.projectHighlights
import com.artkachenko.livetextfinder.search.SearchPresentationOwner
import com.artkachenko.livetextfinder.search.drawableElements
import com.artkachenko.livetextfinder.utils.isKeyboardVisible
import com.artkachenko.livetextfinder.utils.MonotonicClock
import kotlinx.coroutines.delay

@Composable
fun App() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            var state by remember { mutableStateOf(OcrUiState()) }
            var query by remember { mutableStateOf("") }
            var previewSize by remember { mutableStateOf(IntSize.Zero) }
            var rootSize by remember { mutableStateOf(IntSize.Zero) }
            var graceTimerRevision by remember { mutableIntStateOf(0) }
            val presentationOwner = remember { SearchPresentationOwner() }
            val renderedQuery = query
            val presentation = remember(query, state, graceTimerRevision) {
                presentationOwner.present(query, state, MonotonicClock.nowMillis())
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
            val compactLayout = (rootSize != IntSize.Zero && rootSize.height / density.density < 500f) ||
                density.fontScale >= 1.3f
            val keyboardVisible = isKeyboardVisible()

            // This is the only IME inset owner. The weighted preview receives the remaining
            // height directly, so OCR status changes do not recalculate its available space.
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().imePadding().onSizeChanged { rootSize = it },
            ) {
                val shouldShowViews = !compactLayout && !keyboardVisible
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
                        onValueChange = { query = it },
                        label = { Text("Find in recognized text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (shouldShowViews) {
                        Text(
                            when {
                                search.snapshot == null && state.status == OcrStatus.Scanning -> "Waiting for fresh recognized text"
                                search.snapshot == null -> "Search paused · see camera status"
                                search.query.isEmpty() -> "Enter text to search"
                                search.occurrences.isEmpty() && presentation.outlinedElements.isNotEmpty() ->
                                    "No current matches · retaining ${presentation.outlinedElements.size} outlines briefly"
                                search.occurrences.isEmpty() -> "No matches in recognized text"
                                !highlights.ready -> "Recognized matches: ${search.occurrences.size} · Highlights waiting for preview"
                                presentation.occurrences.any { it.firstMissingAtMillis != null } ->
                                    "Recognized matches: ${search.occurrences.size} · outlined elements: ${presentation.outlinedElements.size} (some briefly retained)"
                                else -> "Recognized matches: ${search.occurrences.size} · outlined elements: ${presentation.outlinedElements.size}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.heightIn(min = 40.dp, max = 40.dp),
                        )
                    }
                }
                if (shouldShowViews) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)
                            .onSizeChanged { previewSize = it },
                    ) {
                        CameraPreview(Modifier.fillMaxSize()) { state = it }
                        Canvas(Modifier.matchParentSize()) {
                            val outlines = drawableElements(
                                query,
                                renderedQuery,
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
                        Modifier.fillMaxWidth().height(88.dp)
                            .verticalScroll(rememberScrollState()).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "OCR: ${state.status.name} · Latin script",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Recognized lines: ${state.snapshot?.lines?.size ?: 0}", maxLines = 1)
                        state.snapshot?.let { snapshot ->
                            Text(
                                "${snapshot.frame.uprightWidth} × ${snapshot.frame.uprightHeight} · rotation ${snapshot.frame.rotationDegrees}°",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                            if (snapshot.text.isNotBlank()) {
                                Text(snapshot.text.take(240), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
