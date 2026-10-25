package com.artkachenko.livetextfinder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.camera.CameraPreview
import com.artkachenko.livetextfinder.ocr.OcrUiState

@Composable
fun App() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            var state by remember { mutableStateOf(OcrUiState()) }
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Live Text Finder", style = MaterialTheme.typography.headlineSmall)
                    Text("On-device OCR · Latin script", style = MaterialTheme.typography.bodyMedium)
                }
                Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                    CameraPreview(Modifier.fillMaxSize()) { state = it }
                }
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("OCR: ${state.status.name}", style = MaterialTheme.typography.labelLarge)
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    Text("Recognized lines: ${state.snapshot?.lines?.size ?: 0}")
                    state.snapshot?.let { snapshot ->
                        Text(
                            "${snapshot.frame.uprightWidth} × ${snapshot.frame.uprightHeight} · " +
                                    "rotation ${snapshot.frame.rotationDegrees}°",
                            style = MaterialTheme.typography.labelSmall,
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
