package com.artkachenko.livetextfinder

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import com.artkachenko.livetextfinder.camera.ImageShareState
import com.artkachenko.livetextfinder.camera.ImageShareStatus
import com.artkachenko.livetextfinder.search.SearchListTransferState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

/** Returns the finished share outcome to announce, or null while idle or preparing. */
internal fun ImageShareState.outcomeMessage(): String? = message?.takeIf {
    status == ImageShareStatus.Opened || status == ImageShareStatus.Error
}

/** Returns the finished import or export outcome to announce, or null while one is running. */
internal fun SearchListTransferState.outcomeMessage(): String? = message?.takeIf { !busy }

/**
 * Shows copy, share and list-transfer outcomes as snackbars on [hostState].
 *
 * Only outcomes that happen while this is composed are shown: a message already present when
 * the screen is created or restored is not replayed. A newer outcome replaces the one showing.
 * A copy outcome is left to the matches dialog while that dialog is open.
 */
@Composable
internal fun FinderFeedbackEffects(
    hostState: SnackbarHostState,
    copySession: StillMatchCopySession,
    shareState: State<ImageShareState>,
    transferState: State<SearchListTransferState>,
) {
    LaunchedEffect(hostState, copySession) {
        snapshotFlow { copySession.feedbackCount }.drop(1).collectLatest {
            val message = copySession.message
            if (message != null && !copySession.dialogOpen) {
                hostState.showSnackbar(message)
            }
        }
    }
    LaunchedEffect(hostState, shareState) {
        snapshotFlow { shareState.value }.drop(1).collectLatest { state ->
            val message = state.outcomeMessage() ?: return@collectLatest
            hostState.showSnackbar(
                message = message,
                duration = outcomeDuration(state.status == ImageShareStatus.Error),
            )
        }
    }
    LaunchedEffect(hostState, transferState) {
        snapshotFlow { transferState.value }.drop(1).collectLatest { state ->
            val message = state.outcomeMessage() ?: return@collectLatest
            hostState.showSnackbar(
                message = message,
                duration = outcomeDuration(state.isError),
            )
        }
    }
}

private fun outcomeDuration(isError: Boolean): SnackbarDuration =
    if (isError) SnackbarDuration.Long else SnackbarDuration.Short
