package com.artkachenko.livetextfinder.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
internal actual fun rememberImportedImageOwner(): ImportedImageOwner = remember {
    ImportedImageOwner()
}

internal actual class ImportedImageOwner actual constructor() {
    private val mutableState = mutableStateOf(
        ImportedImageState(phase = ImportedImagePhase.Unsupported),
    )

    internal actual val state: State<ImportedImageState> = mutableState

    internal actual fun beginPicker(): Long? = null

    internal actual fun cancel() = Unit

    internal actual fun stop() = Unit

    internal actual fun dispose() = Unit
}

@Composable
internal actual fun ImportedImagePicker(owner: ImportedImageOwner) = Unit

@Composable
internal actual fun ImportedImagePreview(
    owner: ImportedImageOwner,
    modifier: Modifier,
    isVisible: () -> Boolean,
) = Unit
