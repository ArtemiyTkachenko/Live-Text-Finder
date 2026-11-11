package com.artkachenko.livetextfinder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

/** Owns feedback and requests for one retained search and each occurrence-dialog opening. */
internal class StillMatchCopySession {
    var dialogOpen by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    /** Increments for every published copy outcome, including a repeated message. */
    var feedbackCount by mutableIntStateOf(0)
        private set
    var openingToken by mutableStateOf<Any?>(null)
        private set
    private var active = true
    private var requestToken: Any? = null

    fun updateDialogOpen(open: Boolean) {
        if (!active || dialogOpen == open) return
        requestToken = null
        openingToken = if (open) Any() else null
        dialogOpen = open
        message = null
    }

    fun acceptsOpening(token: Any?): Boolean = active && openingToken === token

    fun fail(token: Any?, failureMessage: String) {
        if (acceptsOpening(token)) {
            requestToken = null
            publish(failureMessage)
        }
    }

    suspend fun copy(
        token: Any?,
        contextIsCurrent: () -> Boolean,
        copiedMessage: String,
        failureMessage: String,
        perform: suspend (() -> Boolean) -> StillMatchCopyOutcome,
    ) {
        if (!acceptsOpening(token)) return
        val request = Any()
        requestToken = request
        val isCurrent = {
            acceptsOpening(token) && requestToken === request && contextIsCurrent()
        }
        if (!isCurrent()) return
        val outcome = try {
            perform(isCurrent)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            StillMatchCopyOutcome.Failed
        }
        if (!isCurrent()) return
        when (outcome) {
            StillMatchCopyOutcome.Copied -> {
                updateDialogOpen(false)
                publish(copiedMessage)
            }
            StillMatchCopyOutcome.Stale,
            StillMatchCopyOutcome.Failed -> publish(failureMessage)
        }
        requestToken = null
    }

    private fun publish(outcome: String) {
        message = outcome
        feedbackCount++
    }

    fun invalidate() {
        active = false
        requestToken = null
        openingToken = null
    }
}

@Composable
internal fun rememberStillMatchCopySession(
    sourceIdentity: String?,
    searchIdentity: String,
): StillMatchCopySession {
    val session = remember(sourceIdentity, searchIdentity) { StillMatchCopySession() }
    DisposableEffect(session) {
        onDispose(session::invalidate)
    }
    return session
}
