package com.artkachenko.livetextfinder.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState

@Composable
internal actual fun rememberFrozenFrameOwner(): FrozenFrameOwner = remember { FrozenFrameOwner() }

internal actual class FrozenFrameOwner actual constructor() {
    private val mutableState = mutableStateOf(FreezePresentationState())

    internal actual val state: State<FreezePresentationState> = mutableState

    internal actual fun freeze(cameraReady: Boolean): Boolean = false

    internal actual fun cancel() {
        mutableState.value = FreezePresentationState()
    }

    internal actual fun resume() {
        mutableState.value = FreezePresentationState()
    }

    internal actual fun stop() {
        mutableState.value = FreezePresentationState()
    }
}

@Composable
internal actual fun CameraPreview(
    modifier: Modifier,
    owner: FrozenFrameOwner,
    isContentVisible: () -> Boolean,
    onState: (OcrUiState) -> Unit,
) {
    LaunchedEffect(Unit) {
        onState(
            OcrUiState(
                OcrStatus.Unavailable,
                "Camera OCR is available on Android in this build.",
            ),
        )
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("Android camera preview", color = Color.White)
    }
}
