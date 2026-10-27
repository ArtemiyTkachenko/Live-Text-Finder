package com.artkachenko.livetextfinder.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.artkachenko.livetextfinder.search.HighlightColor
import com.artkachenko.livetextfinder.search.TextSearchResult

@Composable
internal actual fun rememberImageShareOwner(): ImageShareOwner = remember { ImageShareOwner() }

internal actual class ImageShareOwner actual constructor() {
    private val mutableState = mutableStateOf(ImageShareState())

    internal actual val state: State<ImageShareState> = mutableState

    internal actual fun cancel() = Unit

    internal actual fun dispose() = Unit

    internal actual fun updateCurrent(
        sourceIdentity: String?,
        searchIdentity: String,
        highlightColor: HighlightColor,
    ) = Unit

    internal fun unsupported() {
        mutableState.value = ImageShareState(
            ImageShareStatus.Error,
            "Still-image sharing is unsupported on this platform.",
        )
    }
}

internal actual fun ImageShareOwner.shareFrozen(
    owner: FrozenFrameOwner,
    search: TextSearchResult,
    currentSourceIdentity: () -> String?,
    currentSearchIdentity: () -> String,
    currentHighlightColor: () -> HighlightColor,
) = unsupported()

internal actual fun ImageShareOwner.shareImported(
    owner: ImportedImageOwner,
    search: TextSearchResult,
    currentSourceIdentity: () -> String?,
    currentSearchIdentity: () -> String,
    currentHighlightColor: () -> HighlightColor,
) = unsupported()
