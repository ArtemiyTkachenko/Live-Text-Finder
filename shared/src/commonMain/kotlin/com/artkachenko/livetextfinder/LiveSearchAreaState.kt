package com.artkachenko.livetextfinder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.artkachenko.livetextfinder.search.LiveSearchAreaDialogSession
import com.artkachenko.livetextfinder.search.LiveSearchAreaEditorSession
import com.artkachenko.livetextfinder.search.LiveSearchAreaOwner
import com.artkachenko.livetextfinder.search.NormalizedSearchArea
import com.artkachenko.livetextfinder.search.normalizeEditorDraftArea

/** One precision-dialog opening and the editing context captured when it opened. */
internal class LiveSearchAreaDialogRequest(
    val token: Long,
    val usesDraft: Boolean,
    val editorToken: Long,
    val revision: Long,
)

/**
 * Holds the live scan area for the screen: the committed area published to composition, the
 * direct-edit draft and the precision dialog request.
 *
 * [owner] stays authoritative. [area] and [revision] are the values last published to
 * composition, so draw-time checks can still compare them with the owner's current values.
 */
internal class LiveSearchAreaState {
    val owner = LiveSearchAreaOwner()
    private val editorSession = LiveSearchAreaEditorSession(owner)
    private val dialogSession = LiveSearchAreaDialogSession()

    var area by mutableStateOf<NormalizedSearchArea?>(null)
        private set
    var revision by mutableLongStateOf(owner.revision)
        private set
    var editorDraft by mutableStateOf<LiveSearchAreaEditorSession.Draft?>(null)
        private set
    var dialogRequest by mutableStateOf<LiveSearchAreaDialogRequest?>(null)
        private set

    /** True when neither a direct-edit draft nor a precision dialog is active right now. */
    val isIdle: Boolean
        get() = editorSession.draft == null && dialogSession.currentToken == null

    /** Discards the draft and any precision dialog while keeping the committed area. */
    fun cancelEditing() {
        editorSession.cancelCurrent()
        publishDraft()
        dialogRequest = null
        dialogSession.invalidate()
    }

    /** Returns to the full frame and invalidates every outstanding draft and dialog. */
    fun reset() {
        owner.reset()
        cancelEditing()
        publishCommitted()
    }

    /** Starts a direct-edit draft and returns its callback token. */
    fun beginEditor(area: NormalizedSearchArea): Long =
        editorSession.begin(area).token.also { publishDraft() }

    fun updateEditor(token: Long, area: NormalizedSearchArea?): Boolean =
        editorSession.update(token, area).also { updated ->
            if (updated) publishDraft()
        }

    fun cancelEditor(token: Long): Boolean =
        editorSession.cancel(token).also { cancelled ->
            if (cancelled) publishDraft()
        }

    /** Commits the draft when [isCurrent] still holds and publishes the new area. */
    fun applyEditor(token: Long, isCurrent: () -> Boolean): Boolean =
        editorSession.apply(token, isCurrent).also { applied ->
            if (applied) {
                publishDraft()
                publishCommitted()
            }
        }

    /** Opens the precision dialog for the committed area or, with [usesDraft], the draft. */
    fun openDialog(usesDraft: Boolean, editorToken: Long) {
        val revisionAtOpen = owner.revision
        dialogRequest = LiveSearchAreaDialogRequest(
            token = dialogSession.open(),
            usesDraft = usesDraft,
            editorToken = editorToken,
            revision = revisionAtOpen,
        )
    }

    /** Returns an action that opens the precision dialog for a still-current draft. */
    fun precisionAction(token: Long, isCurrent: () -> Boolean): () -> Unit =
        editorSession.precisionAction(token, isCurrent) { editorToken ->
            openDialog(usesDraft = true, editorToken = editorToken)
        }

    /** Closes [request] unless a newer dialog replaced it. */
    fun dismissDialog(request: LiveSearchAreaDialogRequest) {
        dialogSession.dismiss(request.token) {
            if (!request.usesDraft) publishCommitted()
            dialogRequest = null
        }
    }

    /** Applies the dialog's area to the draft or the committed area it was opened for. */
    fun applyDialogArea(request: LiveSearchAreaDialogRequest, area: NormalizedSearchArea?) {
        dialogSession.dispatch(request.token) {
            if (request.usesDraft) {
                if (editorSession.draft?.token == request.editorToken &&
                    owner.revision == request.revision
                ) {
                    updateEditor(request.editorToken, normalizeEditorDraftArea(area))
                }
            } else if (owner.revision == request.revision &&
                owner.apply(request.revision, area)
            ) {
                publishCommitted()
            }
        }
    }

    private fun publishDraft() {
        editorDraft = editorSession.draft
    }

    private fun publishCommitted() {
        area = owner.area
        revision = owner.revision
    }
}
