package com.artkachenko.livetextfinder

import androidx.compose.runtime.saveable.SaverScope
import com.artkachenko.livetextfinder.search.HighlightColor
import com.artkachenko.livetextfinder.search.SearchMatchMode
import com.artkachenko.livetextfinder.search.inMemorySearchSessionController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchListManagerStateTest {
    private val session = inMemorySearchSessionController()
    private var transferNotifications = 0
    private val state = SearchListManagerState(session) { transferNotifications++ }

    @Test
    fun toggle_opensAndClosesManagerAndDiscardsDraft() {
        state.toggle()
        state.startNewList()
        assertTrue(state.isOpen)
        assertTrue(state.editor.isOpen)

        state.toggle()

        assertFalse(state.isOpen)
        assertFalse(state.editor.isOpen)
        assertEquals(3, transferNotifications)
        assertEquals(3L, state.contextRevision)
    }

    @Test
    fun importedDraft_opensManagerWithoutNotifyingTransferOwner() {
        state.acceptImportedDraft("Fruit", listOf("apple", "pear"))

        assertTrue(state.isOpen)
        assertEquals("Fruit", state.editor.name)
        assertEquals("apple\npear", state.editor.terms)
        assertEquals(0, transferNotifications)
        assertEquals(1L, state.contextRevision)
    }

    @Test
    fun saveDraft_storesListAndClosesEditor() {
        state.startNewList()
        state.updateDraftName("Fruit")
        state.updateDraftTerms("apple\npear")

        state.saveDraft()

        val saved = session.state.value.persisted.lists.single()
        assertEquals("Fruit", saved.name)
        assertEquals(listOf("apple", "pear"), saved.terms)
        assertFalse(state.editor.isOpen)
        assertEquals(4, transferNotifications)
    }

    @Test
    fun saveDraft_invalidDraftKeepsEditorOpenWithError() {
        state.startNewList()

        state.saveDraft()

        assertTrue(state.editor.isOpen)
        assertNotNull(state.editor.error)
        assertTrue(session.state.value.persisted.lists.isEmpty())
    }

    @Test
    fun selectList_notifiesOnlyForChangedSelection() {
        val id = saveList("Fruit", "apple")
        val notificationsAfterSave = transferNotifications

        assertEquals(id, session.state.value.persisted.activeListId)

        state.selectList(id)

        assertEquals(notificationsAfterSave, transferNotifications)

        state.selectList(null)

        assertNull(session.state.value.persisted.activeListId)
        assertEquals(notificationsAfterSave + 1, transferNotifications)

        state.selectList(id)

        assertEquals(id, session.state.value.persisted.activeListId)
        assertEquals(notificationsAfterSave + 2, transferNotifications)
    }

    @Test
    fun confirmDelete_removesListAndClosesItsEditor() {
        val id = saveList("Fruit", "apple")
        val list = session.state.value.persisted.lists.single()
        state.editList(list)
        state.requestDelete(list)
        assertEquals(id, state.pendingDeleteId)

        state.confirmDelete(id)

        assertTrue(session.state.value.persisted.lists.isEmpty())
        assertFalse(state.editor.isOpen)
        assertNull(state.pendingDeleteId)
    }

    @Test
    fun dismissDelete_keepsListAndEditor() {
        saveList("Fruit", "apple")
        val list = session.state.value.persisted.lists.single()
        state.editList(list)
        state.requestDelete(list)

        state.dismissDelete()

        assertNull(state.pendingDeleteId)
        assertEquals(listOf(list), session.state.value.persisted.lists)
        assertEquals(list.id, state.editor.editingId)
    }

    @Test
    fun savedSearchEdits_notifyTransferOwnerBeforeChangingSession() {
        state.setManualQuery("apple")
        state.setMatchMode(SearchMatchMode.WholeWord)
        state.setHighlightColor(HighlightColor.Cyan)
        state.retrySavedSearches()

        assertEquals("apple", session.state.value.persisted.manualQuery)
        assertEquals(SearchMatchMode.WholeWord, session.state.value.persisted.matchMode)
        assertEquals(HighlightColor.Cyan, session.state.value.persisted.highlightColor)
        assertEquals(4, transferNotifications)
        assertEquals(4L, state.contextRevision)
    }

    @Test
    fun saver_restoresOpenManagerDraftAndPendingDelete() {
        val id = saveList("Fruit", "apple")
        val list = session.state.value.persisted.lists.single()
        state.toggle()
        state.editList(list)
        state.updateDraftTerms("apple\npear")
        state.requestDelete(list)
        val saver = SearchListManagerState.saver(session) { transferNotifications++ }
        val notificationsBeforeRestore = transferNotifications

        val saved = with(saver) { SaverScope { true }.save(state) }
        val restored = assertNotNull(saver.restore(assertNotNull(saved)))

        assertTrue(restored.isOpen)
        assertEquals(state.editor, restored.editor)
        assertEquals("apple\npear", restored.editor.terms)
        assertEquals(id, restored.pendingDeleteId)
        assertEquals(0L, restored.contextRevision)
        assertEquals(notificationsBeforeRestore, transferNotifications)
    }

    private fun saveList(name: String, terms: String): String {
        state.startNewList()
        state.updateDraftName(name)
        state.updateDraftTerms(terms)
        state.saveDraft()
        return session.state.value.persisted.lists.single().id
    }
}
