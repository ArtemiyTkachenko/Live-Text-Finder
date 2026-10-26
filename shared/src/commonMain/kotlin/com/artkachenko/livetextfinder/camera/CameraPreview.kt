package com.artkachenko.livetextfinder.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.artkachenko.livetextfinder.ocr.OcrUiState

@Composable
internal expect fun rememberFrozenFrameOwner(): FrozenFrameOwner

internal expect class FrozenFrameOwner() {
    internal val state: State<FreezePresentationState>

    internal fun freeze(cameraReady: Boolean): Boolean

    internal fun cancel()

    internal fun resume()

    internal fun stop()
}

@Composable
internal expect fun CameraPreview(
    modifier: Modifier,
    owner: FrozenFrameOwner,
    isContentVisible: () -> Boolean,
    onState: (OcrUiState) -> Unit,
)
