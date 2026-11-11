package com.artkachenko.livetextfinder.search

/** Owns ephemeral area state and rejects edits created before a reset or a newer edit. */
internal class LiveSearchAreaOwner {
    var area: NormalizedSearchArea? = null
        private set
    var revision: Long = 0
        private set

    fun apply(expectedRevision: Long, newArea: NormalizedSearchArea?): Boolean {
        if (expectedRevision != revision) return false
        if (area != newArea) {
            area = newArea
            revision++
        }
        return true
    }

    fun reset() {
        area = null
        revision++
    }
}

/** Owns one temporary UI draft and its callback token; committed area remains in the owner. */
internal class LiveSearchAreaEditorSession(private val owner: LiveSearchAreaOwner) {
    internal data class Draft(
        val token: Long,
        val expectedRevision: Long,
        val area: NormalizedSearchArea?,
    )

    var draft: Draft? = null
        private set
    private var nextToken = 0L

    fun begin(area: NormalizedSearchArea): Draft {
        nextToken++
        return Draft(nextToken, owner.revision, area).also { draft = it }
    }

    fun update(token: Long, area: NormalizedSearchArea?): Boolean {
        val current = draft?.takeIf { it.token == token } ?: return false
        draft = current.copy(area = area)
        return true
    }

    fun cancel(token: Long): Boolean {
        if (draft?.token != token) return false
        draft = null
        return true
    }

    fun cancelCurrent() {
        draft = null
        nextToken++
    }

    fun precisionAction(
        token: Long,
        isCurrent: () -> Boolean,
        onOpen: (Long) -> Unit,
    ): () -> Unit = {
        val current = draft
        if (current?.token == token && current.expectedRevision == owner.revision && isCurrent()) {
            onOpen(token)
        }
    }

    fun apply(token: Long, isCurrent: () -> Boolean): Boolean {
        val current = draft?.takeIf { it.token == token } ?: return false
        if (!isCurrent() || !owner.apply(current.expectedRevision, current.area)) return false
        draft = null
        return true
    }
}

/** Guards one precision dialog instance so stale Apply/Cancel callbacks cannot affect a reopen. */
internal class LiveSearchAreaDialogSession {
    private var nextToken = 0L
    var currentToken: Long? = null
        private set

    fun open(): Long {
        nextToken++
        currentToken = nextToken
        return nextToken
    }

    fun dispatch(token: Long, action: () -> Unit): Boolean {
        if (currentToken != token) return false
        action()
        return true
    }

    fun dismiss(token: Long, action: () -> Unit): Boolean {
        if (currentToken != token) return false
        currentToken = null
        action()
        return true
    }

    fun invalidate() {
        currentToken = null
    }
}
