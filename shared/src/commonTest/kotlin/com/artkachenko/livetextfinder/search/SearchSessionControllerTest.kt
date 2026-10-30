package com.artkachenko.livetextfinder.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchSessionControllerTest {
    private class ControlledStore : SearchSessionStore {
        var loadCallback: ((Result<PersistedSearchState?>) -> Unit)? = null
        val writes = mutableListOf<Pair<PersistedSearchState, (Result<Unit>) -> Unit>>()
        var resets = 0
        var resetCallback: ((Result<Unit>) -> Unit)? = null
        override fun load(callback: (Result<PersistedSearchState?>) -> Unit) {
            loadCallback = callback
        }

        override fun save(value: PersistedSearchState, callback: (Result<Unit>) -> Unit) {
            writes += value to callback
        }

        override fun reset(callback: (Result<Unit>) -> Unit) {
            resets++
            resetCallback = callback
        }

        fun completeLoad(value: PersistedSearchState?) {
            loadCallback!!.invoke(Result.success(value))
        }

        fun failLoad(message: String) {
            loadCallback!!.invoke(Result.failure(IllegalStateException(message)))
        }

        fun completeWrite(index: Int, result: Result<Unit> = Result.success(Unit)) {
            writes[index].second(result)
        }

        fun completeReset(result: Result<Unit> = Result.success(Unit)) {
            resetCallback!!.invoke(result)
        }
    }

    @Test
    fun highlightColorWaitsForHealthyLoadAndPreservesSearchIdentity() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        val beforeLoad = session.state.value.persisted
        session.setHighlightColor(HighlightColor.Cyan)
        assertEquals(beforeLoad, session.state.value.persisted)
        assertTrue(store.writes.isEmpty())

        val saved = PersistedSearchState(
            "manual",
            listOf(SavedSearchList("stable", "Parts", listOf("foo"))),
            "stable",
        )
        store.completeLoad(saved)
        val identity = session.state.value.activeSearch.identity
        session.setHighlightColor(HighlightColor.Magenta)

        assertEquals(HighlightColor.Magenta, session.state.value.persisted.highlightColor)
        assertEquals(saved.manualQuery, session.state.value.persisted.manualQuery)
        assertEquals(saved.lists, session.state.value.persisted.lists)
        assertEquals(saved.activeListId, session.state.value.persisted.activeListId)
        assertEquals(identity, session.state.value.activeSearch.identity)
        assertEquals(HighlightColor.Magenta, store.writes.single().first.highlightColor)
    }

    @Test
    fun highlightColorIsBlockedDuringRecoveryAndResetReturnsToYellow() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        val customColor = HighlightColor.fromRgb(18, 171, 239)
        store.failLoad("unsupported version")
        session.setHighlightColor(customColor)

        assertEquals(HighlightColor.Yellow, session.state.value.persisted.highlightColor)
        assertTrue(store.writes.isEmpty())

        session.retry()
        store.completeReset()
        assertEquals(HighlightColor.Yellow, session.state.value.persisted.highlightColor)
        assertEquals(HighlightColor.Yellow, store.writes.single().first.highlightColor)
    }

    @Test
    fun customHighlightColorPreservesSearchIdentityAndSameEffectiveColorIsNoOp() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        val custom = HighlightColor.fromRgb(18, 171, 239)
        session.setHighlightColor(custom)
        assertEquals(HighlightColor.Yellow, session.state.value.persisted.highlightColor)
        assertTrue(store.writes.isEmpty())
        store.completeLoad(
            PersistedSearchState(
                "manual",
                listOf(SavedSearchList("stable", "Parts", listOf("foo"))),
                "stable",
            ),
        )
        val identity = session.state.value.activeSearch.identity

        session.setHighlightColor(custom)
        session.setHighlightColor(HighlightColor(0xFF12ABEF))

        assertEquals(custom, session.state.value.persisted.highlightColor)
        assertEquals(identity, session.state.value.activeSearch.identity)
        assertEquals(1, store.writes.size)
        assertEquals(custom, store.writes.single().first.highlightColor)
    }

    @Test
    fun lateLoadMergesManualEditAndQueuedColorWritesKeepLatestValue() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        val customColor = HighlightColor.fromRgb(18, 171, 239)
        session.setManualQuery("typed before load")
        session.setHighlightColor(HighlightColor.Cyan)
        store.completeLoad(
            PersistedSearchState(
                "stored query",
                listOf(SavedSearchList("stable", "Parts", listOf("foo"))),
                "stable",
                HighlightColor.White,
            ),
        )
        assertEquals("typed before load", session.state.value.persisted.manualQuery)
        assertNull(session.state.value.persisted.activeListId)
        assertEquals(HighlightColor.White, session.state.value.persisted.highlightColor)
        assertEquals(1, store.writes.size)

        session.setHighlightColor(HighlightColor.Cyan)
        session.setHighlightColor(customColor)
        assertEquals(1, store.writes.size)
        store.completeWrite(0)
        assertEquals(2, store.writes.size)
        assertEquals("typed before load", store.writes[1].first.manualQuery)
        assertEquals(customColor, store.writes[1].first.highlightColor)
        assertEquals(listOf("foo"), store.writes[1].first.lists.single().terms)

        store.completeWrite(1, Result.failure(IllegalStateException("disk full")))
        session.retry()
        assertEquals(3, store.writes.size)
        assertEquals(customColor, store.writes[2].first.highlightColor)
        store.completeWrite(2)
        assertNull(session.state.value.error)
    }

    @Test
    fun lateLoadKeepsQueryTypingAndExplicitClearAndDoesNotWriteBeforeLoad() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        session.setManualQuery("typed during load")
        assertTrue(store.writes.isEmpty())
        store.completeLoad(
            PersistedSearchState(
                "old query",
                listOf(SavedSearchList("saved", "Parts", listOf("foo", "bar baz"))),
                "saved",
            ),
        )
        assertEquals("typed during load", session.state.value.persisted.manualQuery)
        assertNull(session.state.value.persisted.activeListId)
        assertEquals(listOf("foo", "bar baz"), session.state.value.persisted.lists.single().terms)
        assertEquals(1, store.writes.size)
        session.setManualQuery("")
        assertEquals("", session.state.value.persisted.manualQuery)
        assertNull(session.state.value.persisted.activeListId)
    }

    @Test
    fun listLifecyclePreservesManualQueryStableIdsAndActiveTermIdentity() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        store.completeLoad(PersistedSearchState("manual phrase", emptyList(), null))
        assertNull(session.saveList(null, "  Parts  ", "foo\nbar baz"))
        val first = session.state.value.persisted.lists.single()
        assertEquals("Parts", first.name)
        session.selectList(first.id)
        assertEquals("manual phrase", session.state.value.persisted.manualQuery)
        val active = session.state.value.activeSearch
        assertEquals(listOf("${first.id}:0", "${first.id}:1"), active.terms.map { it.id })
        assertNull(session.saveList(first.id, "Parts", "foo\nquux"))
        assertNotEquals(active.identity, session.state.value.activeSearch.identity)
        session.deleteList(first.id)
        assertNull(session.state.value.persisted.activeListId)
        assertEquals("manual phrase", session.state.value.persisted.manualQuery)
        assertNull(session.saveList(null, "parts", "one"))
        assertNotEquals(first.id, session.state.value.persisted.lists.single().id)
    }

    @Test
    fun listEditsKeepSurvivorSlotsAndAllocateAfterReservingAllSurvivors() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        store.completeLoad(null)
        assertNull(session.saveList(null, "Parts", "zebra"))
        val initial = session.state.value.persisted.lists.single()
        assertEquals(listOf(0), initial.paletteSlots)

        assertNull(session.saveList(initial.id, "Parts", "aardvark\nzebra"))
        val added = session.state.value.persisted.lists.single()
        assertEquals(listOf(1, 0), added.paletteSlots)

        assertNull(session.saveList(initial.id, "Renamed", "ZEBRA"))
        val renamed = session.state.value.persisted.lists.single()
        assertEquals(listOf(0), renamed.paletteSlots)
        assertEquals("Renamed", renamed.name)

        assertNull(session.saveList(initial.id, "Renamed", "zebra\nnew"))
        assertEquals(listOf(0, 1), session.state.value.persisted.lists.single().paletteSlots)
    }

    @Test
    fun listEditUsesIgnoreCaseSurvivorsAndReusesFreedSlotsInInputOrder() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        store.completeLoad(null)
        assertNull(session.saveList(null, "Greek", "ς\nalpha"))
        val initial = session.state.value.persisted.lists.single()

        assertTrue("ς".lowercase() != "σ".lowercase())
        assertTrue("ς".equals("σ", ignoreCase = true))
        assertNull(session.saveList(initial.id, "Greek", "σ\nnew"))

        val edited = session.state.value.persisted.lists.single()
        assertEquals(initial.paletteSlots.first(), edited.paletteSlots.first())
        assertEquals(0, edited.paletteSlots.last())
        assertEquals(listOf("σ", "new"), edited.terms)
    }

    @Test
    fun replacingThirtyTwoTermsNeverCollidesAndUsesTheFreedSlot() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        store.completeLoad(null)
        val terms = (0 until MAX_TERMS_PER_LIST).map { "term-$it" }
        assertNull(session.saveList(null, "Full", terms.joinToString("\n")))
        val initial = session.state.value.persisted.lists.single()

        val replacementTerms = listOf("replacement") + terms.drop(1)
        assertNull(session.saveList(initial.id, "Full", replacementTerms.joinToString("\n")))

        val updated = session.state.value.persisted.lists.single()
        assertEquals(MAX_TERMS_PER_LIST, updated.paletteSlots.toSet().size)
        assertEquals(initial.paletteSlots.first(), updated.paletteSlots.first())
        assertEquals(initial.paletteSlots.drop(1), updated.paletteSlots.drop(1))
    }

    @Test
    fun listValidationRejectsInvalidOrDuplicateInputWithoutDiscardingLines() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        store.completeLoad(null)
        assertTrue(session.saveList(null, "  ", "foo")!!.contains("name"))
        assertTrue(session.saveList(null, "Parts", "foo\n ")!!.contains("blank"))
        assertTrue(session.saveList(null, "Parts", "foo\nFOO")!!.contains("unique"))
        assertNull(session.saveList(null, "Parts", "new york\nfoo"))
        assertEquals(listOf("new york", "foo"), session.state.value.persisted.lists.single().terms)
    }

    @Test
    fun writesSerializeAndOnlyLatestCompletionControlsSavedState() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        store.completeLoad(null)
        session.setManualQuery("one")
        session.setManualQuery("two")
        assertEquals(1, store.writes.size)
        store.completeWrite(0)
        assertEquals(2, store.writes.size)
        assertEquals("two", store.writes[1].first.manualQuery)
        assertTrue(session.state.value.saving)
        store.completeWrite(1, Result.failure(IllegalStateException("disk full")))
        assertFalse(session.state.value.saving)
        assertEquals("disk full", session.state.value.error)
        session.retry()
        assertEquals(3, store.writes.size)
        assertEquals("two", store.writes[2].first.manualQuery)
        store.completeWrite(2)
        assertNull(session.state.value.error)
        assertFalse(session.state.value.saving)
    }

    @Test
    fun explicitClearBeforeLateLoadOverridesStoredQueryAndSelection() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        session.setManualQuery("typed before clearing")
        session.setManualQuery("")
        store.completeLoad(
            PersistedSearchState(
                "stored query",
                listOf(SavedSearchList("saved", "Parts", listOf("foo"))),
                "saved",
            ),
        )

        assertEquals("", session.state.value.persisted.manualQuery)
        assertNull(session.state.value.persisted.activeListId)
        assertEquals(1, session.state.value.persisted.lists.size)
        assertEquals(1, store.writes.size)
        assertEquals("", store.writes.single().first.manualQuery)
    }

    @Test
    fun selectedListAndManualQueryRestoreTogetherAcrossControllerInstances() {
        val stored = PersistedSearchState(
            "kept manual query",
            listOf(SavedSearchList("stable", "Parts", listOf("foo", "new york"))),
            "stable",
        )
        val store = ControlledStore()
        val session = SearchSessionController(store)
        store.completeLoad(stored)
        assertEquals("kept manual query", session.state.value.persisted.manualQuery)
        assertEquals("stable", session.state.value.persisted.activeListId)
        assertEquals("Parts", session.state.value.activeList?.name)
    }

    @Test
    fun recoveryErrorSurvivesTypingUntilExplicitReset() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        store.failLoad("unsupported version")
        assertTrue(session.state.value.recoveryRequired)

        session.setManualQuery("typed while recovering")
        session.setManualQuery("")

        assertTrue(session.state.value.recoveryRequired)
        assertEquals("unsupported version", session.state.value.error)
        assertTrue(store.writes.isEmpty())
        session.retry()
        assertEquals(1, store.resets)
        session.setManualQuery("typed during reset")
        store.completeReset()
        assertTrue(session.state.value.loaded)
        assertFalse(session.state.value.recoveryRequired)
        assertEquals("typed during reset", session.state.value.persisted.manualQuery)
        assertEquals("typed during reset", store.writes.last().first.manualQuery)
    }

    @Test
    fun failedResetKeepsEditsMadeWhileResetWasInFlight() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        store.failLoad("malformed file")
        session.retry()
        session.setManualQuery("latest typed query")

        store.completeReset(Result.failure(IllegalStateException("reset failed")))

        assertTrue(session.state.value.recoveryRequired)
        assertEquals("latest typed query", session.state.value.persisted.manualQuery)
        assertEquals("reset failed", session.state.value.error)
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun manualQueryLimitIsVisibleAndNeverQueuedForPersistence() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        store.completeLoad(null)
        val boundary = "x".repeat(MAX_MANUAL_QUERY_LENGTH)
        session.setManualQuery(boundary)
        assertEquals(boundary, store.writes.single().first.manualQuery)
        store.completeWrite(0)

        val tooLong = boundary + "x"
        session.setManualQuery(tooLong)
        assertEquals(boundary, session.state.value.persisted.manualQuery)
        assertTrue(session.state.value.error!!.contains("at most"))
        assertEquals(1, store.writes.size)
    }

    @Test
    fun acceptedQuerySurvivesOversizeRejectionAndStoredLoad() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        session.setManualQuery("accepted query")
        session.setManualQuery("x".repeat(MAX_MANUAL_QUERY_LENGTH + 1))
        assertTrue(store.writes.isEmpty())
        val stored = PersistedSearchState(
            "older query",
            listOf(SavedSearchList("saved", "Parts", listOf("foo"))),
            "saved",
        )

        store.completeLoad(stored)

        val expected = stored.copy(manualQuery = "accepted query", activeListId = null)
        assertEquals(expected, session.state.value.persisted)
        assertEquals(expected, store.writes.single().first)
        assertTrue(session.state.value.saving)
        assertTrue(session.state.value.error!!.contains("at most"))

        store.completeWrite(0)

        assertFalse(session.state.value.saving)
        assertTrue(session.state.value.error!!.contains("at most"))
    }

    @Test
    fun acceptedQuerySurvivesOversizeRejectionAndMissingLoad() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        session.setManualQuery("accepted query")
        session.setManualQuery("x".repeat(MAX_MANUAL_QUERY_LENGTH + 1))
        assertTrue(store.writes.isEmpty())

        store.completeLoad(null)

        val expected = PersistedSearchState("accepted query", emptyList(), null)
        assertEquals(expected, session.state.value.persisted)
        assertEquals(expected, store.writes.single().first)
        assertTrue(session.state.value.saving)
        assertTrue(session.state.value.error!!.contains("at most"))

        store.completeWrite(0)

        assertFalse(session.state.value.saving)
        assertTrue(session.state.value.error!!.contains("at most"))
    }

    @Test
    fun explicitClearSurvivesOversizeRejectionAndStoredLoad() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        session.setManualQuery("accepted query")
        session.setManualQuery("")
        session.setManualQuery("x".repeat(MAX_MANUAL_QUERY_LENGTH + 1))
        assertTrue(store.writes.isEmpty())
        val stored = PersistedSearchState(
            "older query",
            listOf(SavedSearchList("saved", "Parts", listOf("foo"))),
            "saved",
        )

        store.completeLoad(stored)

        val expected = stored.copy(manualQuery = "", activeListId = null)
        assertEquals(expected, session.state.value.persisted)
        assertEquals(expected, store.writes.single().first)
        assertTrue(session.state.value.saving)
        assertTrue(session.state.value.error!!.contains("at most"))

        store.completeWrite(0)

        assertFalse(session.state.value.saving)
        assertTrue(session.state.value.error!!.contains("at most"))
    }

    @Test
    fun rejectedLongQueryBeforeLoadCannotReplaceStoredQueryOrSelection() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        session.setManualQuery("x".repeat(MAX_MANUAL_QUERY_LENGTH + 1))
        store.completeLoad(
            PersistedSearchState(
                "stored manual query",
                listOf(SavedSearchList("saved", "Parts", listOf("foo"))),
                "saved",
            ),
        )

        assertEquals("stored manual query", session.state.value.persisted.manualQuery)
        assertEquals("saved", session.state.value.persisted.activeListId)
        assertTrue(session.state.value.error!!.contains("at most"))
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun editingDeletedListIdReturnsVisibleErrorAndDoesNotClaimSuccess() {
        val store = ControlledStore()
        val session = SearchSessionController(store)
        store.completeLoad(null)
        assertNull(session.saveList(null, "Parts", "foo"))
        val id = session.state.value.persisted.lists.single().id
        session.deleteList(id)
        val before = session.state.value.persisted

        val error = session.saveList(id, "Parts", "bar")

        assertTrue(error!!.contains("no longer exists"))
        assertEquals(before, session.state.value.persisted)
    }
}
