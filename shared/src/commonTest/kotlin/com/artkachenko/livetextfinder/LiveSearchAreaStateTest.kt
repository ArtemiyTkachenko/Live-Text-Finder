package com.artkachenko.livetextfinder

import com.artkachenko.livetextfinder.search.NormalizedSearchArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveSearchAreaStateTest {
    private val first = NormalizedSearchArea(0.1f, 0.1f, 0.6f, 0.6f)
    private val second = NormalizedSearchArea(0.2f, 0.2f, 0.8f, 0.8f)

    @Test
    fun applyEditor_publishesCommittedAreaAndClosesDraft() {
        val state = LiveSearchAreaState()
        val token = state.beginEditor(first)

        assertFalse(state.isIdle)
        assertEquals(first, state.editorDraft?.area)
        assertTrue(state.updateEditor(token, second))
        assertTrue(state.applyEditor(token) { true })

        assertNull(state.editorDraft)
        assertEquals(second, state.area)
        assertEquals(state.owner.revision, state.revision)
        assertTrue(state.isIdle)
    }

    @Test
    fun applyEditor_rejectedContextKeepsDraftAndCommittedArea() {
        val state = LiveSearchAreaState()
        val token = state.beginEditor(first)

        assertFalse(state.applyEditor(token) { false })

        assertEquals(first, state.editorDraft?.area)
        assertNull(state.area)
        assertNull(state.owner.area)
    }

    @Test
    fun staleEditorToken_cannotUpdateCancelOrApply() {
        val state = LiveSearchAreaState()
        val stale = state.beginEditor(first)
        state.cancelEditing()
        val current = state.beginEditor(second)

        assertFalse(state.updateEditor(stale, first))
        assertFalse(state.cancelEditor(stale))
        assertFalse(state.applyEditor(stale) { true })
        assertEquals(second, state.editorDraft?.area)
        assertTrue(state.cancelEditor(current))
        assertNull(state.editorDraft)
    }

    @Test
    fun reset_clearsAreaDraftAndDialogAndAdvancesRevision() {
        val state = LiveSearchAreaState()
        val token = state.beginEditor(first)
        state.applyEditor(token) { true }
        val committedRevision = state.revision
        state.beginEditor(second)
        state.openDialog(usesDraft = true, editorToken = 0L)

        state.reset()

        assertNull(state.area)
        assertNull(state.editorDraft)
        assertNull(state.dialogRequest)
        assertTrue(state.revision > committedRevision)
        assertEquals(state.owner.revision, state.revision)
        assertTrue(state.isIdle)
    }

    @Test
    fun committedDialog_appliesAreaAndIgnoresReplacedRequest() {
        val state = LiveSearchAreaState()
        state.openDialog(usesDraft = false, editorToken = 0L)
        val replaced = assertNotNull(state.dialogRequest)
        state.openDialog(usesDraft = false, editorToken = 0L)
        val current = assertNotNull(state.dialogRequest)

        state.applyDialogArea(replaced, first)
        assertNull(state.area)

        state.applyDialogArea(current, second)
        assertEquals(second, state.area)
        assertEquals(state.owner.revision, state.revision)

        state.dismissDialog(replaced)
        assertNotNull(state.dialogRequest)
        state.dismissDialog(current)
        assertNull(state.dialogRequest)
        assertTrue(state.isIdle)
    }

    @Test
    fun draftDialog_updatesDraftWithoutCommitting() {
        val state = LiveSearchAreaState()
        val token = state.beginEditor(first)
        state.precisionAction(token) { true }()
        val request = assertNotNull(state.dialogRequest)

        assertTrue(request.usesDraft)
        state.applyDialogArea(request, second)

        assertEquals(second, state.editorDraft?.area)
        assertNull(state.area)
        assertNull(state.owner.area)
    }

    @Test
    fun startEditing_onPreviewBeginsDraftFromCommittedOrDefaultArea() {
        val state = LiveSearchAreaState()

        state.startEditing(onPreview = true)

        assertEquals(NormalizedSearchArea.CENTERED_DEFAULT, state.editorDraft?.area)
        assertNull(state.dialogRequest)

        state.applyEditor(assertNotNull(state.editorDraft).token) { true }
        state.startEditing(onPreview = true)

        assertEquals(state.area, state.editorDraft?.area)
    }

    @Test
    fun startEditing_offPreviewOpensCommittedAreaDialog() {
        val state = LiveSearchAreaState()

        state.startEditing(onPreview = false)

        val request = assertNotNull(state.dialogRequest)
        assertFalse(request.usesDraft)
        assertNull(state.editorDraft)
    }

    @Test
    fun precisionAction_staleContextDoesNotOpenDialog() {
        val state = LiveSearchAreaState()
        val token = state.beginEditor(first)

        state.precisionAction(token) { false }()

        assertNull(state.dialogRequest)
    }
}
