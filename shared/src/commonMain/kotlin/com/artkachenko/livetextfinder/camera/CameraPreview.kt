package com.artkachenko.livetextfinder.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.artkachenko.livetextfinder.ocr.OcrUiState

@Composable
internal expect fun CameraPreview(modifier: Modifier, onState: (OcrUiState) -> Unit)
