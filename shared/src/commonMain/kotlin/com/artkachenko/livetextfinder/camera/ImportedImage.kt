package com.artkachenko.livetextfinder.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot

/** Lifecycle state for one in-memory imported still image. */
internal data class ImportedImageState(
    val phase: ImportedImagePhase = ImportedImagePhase.Live,
    val requestId: Long? = null,
    val snapshot: ImportedTextSnapshot? = null,
    val message: String? = null,
)

internal enum class ImportedImagePhase {
    Live,
    Picking,
    Reading,
    Recognizing,
    Imported,
    Error,
    Unsupported,
}

@Composable
internal expect fun rememberImportedImageOwner(): ImportedImageOwner

internal expect class ImportedImageOwner() {
    internal val state: State<ImportedImageState>

    internal fun beginPicker(): Long?

    internal fun cancel()

    internal fun stop()

    internal fun dispose()
}

@Composable
internal expect fun ImportedImagePicker(owner: ImportedImageOwner)

@Composable
internal expect fun ImportedImagePreview(
    owner: ImportedImageOwner,
    modifier: Modifier,
    isVisible: () -> Boolean,
)
