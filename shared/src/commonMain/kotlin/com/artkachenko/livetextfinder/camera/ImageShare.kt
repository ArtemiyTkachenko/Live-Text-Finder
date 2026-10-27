package com.artkachenko.livetextfinder.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.artkachenko.livetextfinder.search.TextSearchResult

/** Visible state for an explicit still-image sharing request. */
internal data class ImageShareState(
    val status: ImageShareStatus = ImageShareStatus.Idle,
    val message: String? = null,
)

internal enum class ImageShareStatus {
    Idle,
    Preparing,
    Opened,
    Error,
}

@Composable
internal expect fun rememberImageShareOwner(): ImageShareOwner

internal expect class ImageShareOwner() {
    internal val state: State<ImageShareState>

    internal fun cancel()

    internal fun dispose()

    internal fun updateCurrent(sourceIdentity: String?, searchIdentity: String)
}

internal expect fun ImageShareOwner.shareFrozen(
    owner: FrozenFrameOwner,
    search: TextSearchResult,
    currentSourceIdentity: () -> String?,
    currentSearchIdentity: () -> String,
)

internal expect fun ImageShareOwner.shareImported(
    owner: ImportedImageOwner,
    search: TextSearchResult,
    currentSourceIdentity: () -> String?,
    currentSearchIdentity: () -> String,
)
