package com.artkachenko.livetextfinder.search

import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SearchPresentationOwnerTest {
    private val identityMatrix = listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private val target = PreviewTarget(400, 200, identityMatrix)
    private val frameIdentity = FrameIdentity(7, 3)
    private val owner = SearchPresentationOwner()

    private data class Box(val text: String, val left: Int, val top: Int = 20, val right: Int = left + 40, val bottom: Int = 50)

    private fun state(
        boxes: List<Box>,
        timestamp: Long,
        receipt: Long,
        status: OcrStatus = OcrStatus.TextRecognized,
        identity: FrameIdentity = frameIdentity,
        previewTarget: PreviewTarget = target,
    ): OcrUiState {
        val elements = boxes.map { box ->
            TextElement(box.text, TextBounds(box.left, box.top, box.right, box.bottom), emptyList())
        }
        val text = boxes.joinToString(" ") { it.text }
        val snapshot = com.artkachenko.livetextfinder.ocr.OcrSnapshot(
            text,
            listOf(TextLine(text, null, emptyList(), elements)),
            FrameInfo(timestamp, 400, 200, 0, TextBounds(0, 0, 400, 200), identityMatrix),
        )
        return OcrUiState(
            status,
            snapshot = snapshot,
            projection = FrameProjection(snapshot, identity, previewTarget),
            receivedAtMillis = receipt,
        )
    }

    private fun present(query: String, state: OcrUiState, now: Long) = owner.present(query, state, now)
    private fun present(
        spec: ActiveSearchSpec,
        state: OcrUiState,
        now: Long,
    ) = owner.present(spec, state, now)
    private fun byLeft(presentation: SearchPresentation, left: Float) =
        presentation.occurrences.first { it.boundsLeft() == left }
    private fun PresentedOccurrence.boundsLeft() = outlines.flatMap { it.points }.minOf { it.x }

    @Test fun oneMissingOccurrenceRetainsItsOwnSourceWhileCurrentMatchUpdates() {
        val both = state(listOf(Box("alpha", 10), Box("alpha", 150)), 1, 0)
        val initial = present("alpha", both, 0)
        val currentA = state(listOf(Box("alpha", 12)), 2, 100)
        val partial = present("alpha", currentA, 100)

        assertEquals(1, partial.search.occurrences.size)
        assertEquals(2, partial.occurrences.size)
        assertEquals(2, partial.outlinedElements.size)
        val updatedA = byLeft(partial, 12f)
        val heldB = byLeft(partial, 150f)
        assertSame(currentA.snapshot, updatedA.snapshot)
        assertNull(updatedA.firstMissingAtMillis)
        assertSame(both.snapshot, heldB.snapshot)
        assertSame(heldB.snapshot, heldB.projection.snapshot)
        assertEquals(100L, heldB.firstMissingAtMillis)
        assertEquals(400L, heldB.expiresAtMillis)
        assertSame(initial.occurrences[1].snapshot, heldB.snapshot)
    }

    @Test fun independentMissingDeadlinesDoNotRenewWhenOtherOccurrenceKeepsUpdating() {
        state(listOf(Box("alpha", 10), Box("alpha", 150)), 1, 0).also { present("alpha", it, 0) }
        val a100 = state(listOf(Box("alpha", 12)), 2, 100)
        assertEquals(400L, present("alpha", a100, 100).expiresAtMillis)
        val a200 = state(listOf(Box("alpha", 14)), 3, 200)
        val at200 = present("alpha", a200, 200)
        assertEquals(400L, at200.expiresAtMillis)
        assertEquals(100L, byLeft(at200, 150f).firstMissingAtMillis)

        val blank = state(emptyList(), 4, 250)
        val at250 = present("alpha", blank, 250)
        assertEquals(400L, at250.expiresAtMillis)
        assertEquals(250L, byLeft(at250, 14f).firstMissingAtMillis)
        val at400 = present("alpha", blank, 400)
        assertEquals(1, at400.occurrences.size)
        assertEquals(14f, byLeft(at400, 14f).boundsLeft())
        assertEquals(550L, at400.expiresAtMillis)
    }

    @Test fun returningNearbyOccurrenceRefreshesItsSourceAndCancelsItsHold() {
        state(listOf(Box("alpha", 10), Box("alpha", 150)), 1, 0).also { present("alpha", it, 0) }
        val partial = state(listOf(Box("alpha", 12)), 2, 100)
        present("alpha", partial, 100)
        val returned = state(listOf(Box("alpha", 14), Box("alpha", 154)), 3, 200)
        val next = present("alpha", returned, 200)

        assertEquals(2, next.occurrences.size)
        assertEquals(2, next.search.occurrences.size)
        assertTrue(next.occurrences.all { it.snapshot === returned.snapshot })
        assertTrue(next.occurrences.all { it.firstMissingAtMillis == null && it.expiresAtMillis == null })
        assertNull(next.expiresAtMillis)
    }

    @Test fun timerRecompositionsDoNotRenewAndEachDeadlineExpiresWithoutOcrCallback() {
        state(listOf(Box("alpha", 10), Box("alpha", 150)), 1, 0).also { present("alpha", it, 0) }
        val latest = state(listOf(Box("alpha", 12)), 2, 100)
        val at100 = present("alpha", latest, 100)
        assertEquals(400L, at100.expiresAtMillis)
        assertEquals(1, present("alpha", latest, 399).search.occurrences.size)
        assertEquals(2, present("alpha", latest, 399).occurrences.size)
        val at400 = present("alpha", latest, 400)
        assertEquals(1, at400.search.occurrences.size)
        assertEquals(1, at400.occurrences.size)
        assertNull(at400.expiresAtMillis)
    }

    @Test fun firstMissingDeadlineIsCappedByOriginalReceiptFreshness() {
        state(listOf(Box("alpha", 10), Box("alpha", 150)), 1, 0).also { present("alpha", it, 0) }
        val latePartial = state(listOf(Box("alpha", 12)), 2, 1_300)
        val held = present("alpha", latePartial, 1_300)
        assertEquals(1_500L, held.expiresAtMillis)
        assertEquals(200L, held.expiresAtMillis!! - checkNotNull(byLeft(held, 150f).firstMissingAtMillis))
        assertEquals(1, present("alpha", latePartial, 1_500).occurrences.size)
    }

    @Test fun identicalWordsAssociateOneToOneByGeometryAfterElementReordering() {
        val original = state(listOf(Box("echo", 10), Box("echo", 150)), 1, 0)
        present("echo", original, 0)
        val reorderedAndMissingLeft = state(listOf(Box("echo", 150), Box("other", 250)), 2, 100)
        val next = present("echo", reorderedAndMissingLeft, 100)

        assertEquals(1, next.search.occurrences.size)
        assertEquals(2, next.occurrences.size)
        val currentRight = next.occurrences.first { it.snapshot === reorderedAndMissingLeft.snapshot }
        val heldLeft = next.occurrences.first { it.snapshot === original.snapshot }
        assertEquals(listOf(0), currentRight.occurrence.elementIndices)
        assertEquals(listOf(0), heldLeft.occurrence.elementIndices)
        assertEquals(150f, currentRight.boundsLeft())
        assertEquals(10f, heldLeft.boundsLeft())
    }

    @Test fun clearlyDisplacedSameTextObservationRetiresOldCopy() {
        val original = state(listOf(Box("echo", 10)), 1, 0)
        present("echo", original, 0)
        val displaced = state(listOf(Box("echo", 300)), 2, 100)
        val next = present("echo", displaced, 100)

        assertEquals(1, next.occurrences.size)
        assertSame(displaced.snapshot, next.occurrences.single().snapshot)
        assertNull(next.expiresAtMillis)
    }

    @Test fun overlappingSameTextOccurrenceDoesNotLeaveAnUnmatchedGhost() {
        val original = state(listOf(Box("echo", 10), Box("echo", 30)), 1, 0)
        present("echo", original, 0)

        val current = state(listOf(Box("echo", 10)), 2, 100)
        val next = present("echo", current, 100)

        assertEquals(1, next.search.occurrences.size)
        assertEquals(1, next.occurrences.size)
        assertSame(current.snapshot, next.occurrences.single().snapshot)
        assertEquals(10f, next.outlinedElements.single().outline.points.minOf { it.x })
    }

    @Test
    fun anotherTermCannotSuppressRetentionAndChangingListIdentityClearsIt() {
        val spec = ActiveSearchSpec(
            "list-one",
            "Parts",
            listOf(SearchTerm("one:0", "alpha"), SearchTerm("one:1", "beta")),
        )
        present(spec, state(listOf(Box("alpha", 10)), 1, 0), 0)
        val betaOnly = state(listOf(Box("beta", 10)), 2, 100)
        val retained = present(spec, betaOnly, 100)
        assertEquals(1, retained.search.occurrences.size)
        assertEquals(setOf("one:0", "one:1"), retained.occurrences.map { it.termId }.toSet())
        assertEquals(2, retained.occurrences.size)

        val otherList = spec.copy(
            identity = "list-two",
            terms = listOf(SearchTerm("two:0", "alpha"), SearchTerm("two:1", "beta")),
        )
        val switched = present(otherList, betaOnly, 110)
        assertEquals(1, switched.occurrences.size)
        assertEquals("two:1", switched.occurrences.single().termId)
    }

    @Test
    fun liveSessionIdentityChangeSuppressesPreviouslyComposedHighlights() {
        val session = inMemorySearchSessionController()
        session.setManualQuery("alpha")
        val composedSearch = session.state.value.activeSearch
        val matchingState = state(listOf(Box("alpha", 10)), 1, 0)
        val presentation = SearchPresentationOwner().present(composedSearch, matchingState, 0)
        session.setManualQuery("beta")

        val drawable = drawableElementsForSession(
            session,
            composedSearch,
            presentation,
            matchingState,
            1,
            400f,
            200f,
        )

        assertTrue(drawable.isEmpty())
    }

    @Test fun segmentationChangeContainingOldPhraseUsesSmallerAreaOverlapAndNoGhost() {
        val split = state(listOf(Box("alpha", 20, right = 35), Box("beta", 40, right = 55)), 1, 0)
        val first = present("alpha beta", split, 0)
        assertEquals(1, first.occurrences.size)
        assertEquals(2, first.occurrences.single().outlines.size)
        assertEquals(listOf(0, 1), first.occurrences.single().occurrence.elementIndices)

        val merged = state(listOf(Box("alpha beta", 10, right = 100)), 2, 100)
        val next = present("alpha beta", merged, 100)
        assertEquals(1, next.search.occurrences.size)
        assertEquals(1, next.occurrences.size)
        assertSame(merged.snapshot, next.occurrences.single().snapshot)
        assertEquals(1, next.outlinedElements.size)
    }

    @Test fun phraseHoldKeepsItsWholeSourceFrameAndEveryParticipatingOutline() {
        val phrase = state(listOf(Box("new", 10, right = 45), Box("york", 50, right = 100)), 1, 0)
        val current = present("new york", phrase, 0)
        val blank = state(listOf(Box("street", 200)), 2, 100)
        val held = present("new york", blank, 100)

        assertEquals(1, current.occurrences.size)
        assertEquals(2, current.occurrences.single().outlines.size)
        assertEquals(0, held.search.occurrences.size)
        assertEquals(1, held.occurrences.size)
        assertSame(phrase.snapshot, held.occurrences.single().snapshot)
        assertSame(phrase.snapshot, held.occurrences.single().projection.snapshot)
        assertEquals(2, held.occurrences.single().outlines.size)
    }

    @Test fun latestMatcherCountsStayTruthfulWhileOtherOccurrencesRemainOutlined() {
        state(listOf(Box("alpha", 10), Box("alpha", 150)), 1, 0).also { present("alpha", it, 0) }
        val latest = state(listOf(Box("alpha", 12)), 2, 100)
        val result = present("alpha", latest, 100)

        assertEquals(1, result.search.occurrences.size)
        assertEquals(2, result.occurrences.size)
        assertEquals(2, result.outlinedElements.size)
        assertEquals(1, result.occurrences.count { it.firstMissingAtMillis == null })
        assertEquals(1, result.occurrences.count { it.firstMissingAtMillis != null })
    }

    @Test fun repeatedQueryOccurrencesInOneElementDrawAndCountOneDistinctOutline() {
        val banana = state(listOf(Box("banana", 10, right = 130)), 1, 0)
        val result = present("an", banana, 0)
        assertEquals(2, result.search.occurrences.size)
        assertEquals(2, result.occurrences.size)
        assertEquals(1, result.outlinedElements.size)
        assertEquals(1, drawableElements("an", "an", result, banana, 1, 400f, 200f).size)
    }

    @Test fun queryGeometrySessionErrorAndFreshnessInvalidationsClearAllEntries() {
        val matching = state(listOf(Box("alpha", 10), Box("alpha", 150)), 1, 0)
        present("alpha", matching, 0)
        val partial = state(listOf(Box("alpha", 12)), 2, 100)
        assertEquals(2, present("alpha", partial, 100).occurrences.size)

        val changedQuery = present("beta", partial, 110)
        assertEquals(0, changedQuery.occurrences.size)
        assertEquals(0, present("alpha", partial.copy(projection = null), 120).occurrences.size)

        present("alpha", matching, 130)
        val newGeneration = state(listOf(Box("alpha", 12)), 3, 140, identity = frameIdentity.copy(generation = 4))
        assertEquals(1, present("alpha", newGeneration, 140).occurrences.size)
        val otherTarget = state(listOf(Box("alpha", 14)), 4, 150, identity = frameIdentity.copy(generation = 5),
            previewTarget = target.copy(width = 200))
        assertEquals(1, present("alpha", otherTarget, 150).occurrences.size)

        val error = OcrUiState(OcrStatus.Error, "recognition failed")
        assertEquals(0, present("alpha", error, 160).occurrences.size)
        assertEquals(0, present("alpha", matching, 1_500).occurrences.size)
    }

    @Test fun drawGuardRejectsNewCallbackBeforeCompositionAndChecksEverySourceDeadline() {
        val a = state(listOf(Box("alpha", 10)), 1, 0)
        val exact = present("alpha", a, 0)
        assertEquals(1, drawableElements("alpha", "alpha", exact, a, 1_499, 400f, 200f).size)
        assertTrue(drawableElements("alpha", "alpha", exact, a, 1_500, 400f, 200f).isEmpty())

        val newer = state(listOf(Box("alpha", 12)), 2, 100)
        assertTrue(drawableElements("alpha", "alpha", exact, newer, 100, 400f, 200f).isEmpty())
        assertTrue(drawableElements("beta", "alpha", exact, a, 100, 400f, 200f).isEmpty())

        val both = state(listOf(Box("alpha", 10), Box("alpha", 150)), 3, 200)
        present("alpha", both, 200)
        val partial = state(listOf(Box("alpha", 12)), 4, 250)
        val held = present("alpha", partial, 250)
        assertEquals(2, drawableElements("alpha", "alpha", held, partial, 549, 400f, 200f).size)
        assertEquals(1, drawableElements("alpha", "alpha", held, partial, 550, 400f, 200f).size)
        assertTrue(drawableElements("alpha", "alpha", held, partial.copy(projection = null), 300, 400f, 200f).isEmpty())
    }

    @Test fun blankCallbackGraceUsesCurrentBlankDiagnosticsAndExpiresAtFirstDeadline() {
        val matching = state(listOf(Box("alpha", 10)), 1, 0)
        present("alpha", matching, 0)
        val blank = state(listOf(Box("other", 150)), 2, 100)
        val held = present("alpha", blank, 100)
        assertSame(blank.snapshot, held.search.snapshot)
        assertEquals(0, held.search.occurrences.size)
        assertEquals(1, held.occurrences.size)
        assertEquals(400L, held.expiresAtMillis)
    }
}
