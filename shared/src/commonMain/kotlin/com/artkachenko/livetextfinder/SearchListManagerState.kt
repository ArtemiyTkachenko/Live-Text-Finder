package com.artkachenko.livetextfinder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.artkachenko.livetextfinder.search.HighlightColor
import com.artkachenko.livetextfinder.search.SavedSearchList
import com.artkachenko.livetextfinder.search.SearchListEditorState
import com.artkachenko.livetextfinder.search.SearchMatchMode
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.dispatchSearchListSelection
import com.artkachenko.livetextfinder.ui.RetainedInspectionActionDispatcher

/**
 * Holds the saved-list manager's visibility, editor draft and pending delete confirmation.
 *
 * Every change that could make a pending list import or export stale goes through
 * [markContextChanged], which notifies the transfer owner and advances [contextRevision].
 */
internal class SearchListManagerState(
    private val session: SearchSessionController,
    private val onTransferContextChanged: () -> Unit,
) {
    var isOpen by mutableStateOf(false)
        private set
    var editor by mutableStateOf(SearchListEditorState())
        private set
    var contextRevision by mutableLongStateOf(0L)
        private set
    var pendingDeleteId by mutableStateOf<String?>(null)
        private set

    /** Invalidates pending list transfers after a search, list or editor change. */
    fun markContextChanged() {
        onTransferContextChanged()
        contextRevision++
    }

    fun setManualQuery(query: String) {
        markContextChanged()
        session.setManualQuery(query)
    }

    fun setMatchMode(matchMode: SearchMatchMode) {
        markContextChanged()
        session.setMatchMode(matchMode)
    }

    fun setHighlightColor(color: HighlightColor) {
        markContextChanged()
        session.setHighlightColor(color)
    }

    /** Reloads saved searches after a failed load, or resets them once a reset is confirmed. */
    fun retrySavedSearches() {
        markContextChanged()
        session.retry()
    }

    /** Opens or closes the manager and discards any editor draft. */
    fun toggle() {
        RetainedInspectionActionDispatcher.toggleLists(
            managerOpen = isOpen,
            onContextChanged = ::markContextChanged,
            setManagerOpen = { open -> isOpen = open },
            collapseSearch = {},
            closeEditor = { editor = editor.close() },
        )
    }

    /** Shows an imported list as an unsaved draft; the transfer owner is the caller. */
    fun acceptImportedDraft(name: String, terms: List<String>) {
        contextRevision++
        editor = editor.importedDraft(name, terms)
        isOpen = true
    }

    fun startNewList() {
        markContextChanged()
        editor = editor.startNew()
    }

    fun editList(list: SavedSearchList) {
        markContextChanged()
        editor = editor.edit(list)
    }

    fun updateDraftName(name: String) {
        markContextChanged()
        editor = editor.copy(name = name)
    }

    fun updateDraftTerms(terms: String) {
        markContextChanged()
        editor = editor.copy(terms = terms)
    }

    /** Saves the draft; a validation error stays on the open editor. */
    fun saveDraft() {
        markContextChanged()
        val error = session.saveList(editor.editingId, editor.name, editor.terms)
        editor = if (error == null) editor.close() else editor.copy(error = error)
    }

    fun cancelDraft() {
        markContextChanged()
        editor = editor.close()
    }

    /** Activates a saved list, or manual search for a null [id]. */
    fun selectList(id: String?) {
        dispatchSearchListSelection(session, id, ::markContextChanged)
    }

    fun requestDelete(list: SavedSearchList) {
        pendingDeleteId = list.id
    }

    fun dismissDelete() {
        pendingDeleteId = null
    }

    /** Deletes the list and closes the editor when it was editing that list. */
    fun confirmDelete(id: String) {
        markContextChanged()
        session.deleteList(id)
        if (editor.editingId == id) {
            editor = editor.close()
        }
        pendingDeleteId = null
    }

    companion object {
        /**
         * Saves the manager's visibility, editor draft and pending delete confirmation, so a
         * half-typed list survives Activity recreation. Restoring does not notify the transfer
         * owner; the next composition gives it the restored context.
         */
        fun saver(
            session: SearchSessionController,
            onTransferContextChanged: () -> Unit,
        ): Saver<SearchListManagerState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.isOpen,
                    state.pendingDeleteId,
                    state.editor.isOpen,
                    state.editor.editingId,
                    state.editor.name,
                    state.editor.terms,
                    state.editor.error,
                )
            },
            restore = { saved ->
                SearchListManagerState(session, onTransferContextChanged).apply {
                    isOpen = saved[0] as Boolean
                    pendingDeleteId = saved[1] as String?
                    editor = SearchListEditorState(
                        isOpen = saved[2] as Boolean,
                        editingId = saved[3] as String?,
                        name = saved[4] as String,
                        terms = saved[5] as String,
                        error = saved[6] as String?,
                    )
                }
            },
        )
    }
}
