package com.artkachenko.livetextfinder.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SearchListEditorStateTest {
    @Test
    fun closingManagerClearsTheEditTargetAndDraftBeforeOpeningNew() {
        val editing = SearchListEditorState().edit(
            SavedSearchList("list-a", "Parts", listOf("alpha", "beta")),
        )
        val closed = editing.close()
        val reopened = closed.startNew()

        assertFalse(closed.isOpen)
        assertNull(reopened.editingId)
        assertEquals("", reopened.name)
        assertEquals("", reopened.terms)
        assertNull(reopened.error)
    }
}
