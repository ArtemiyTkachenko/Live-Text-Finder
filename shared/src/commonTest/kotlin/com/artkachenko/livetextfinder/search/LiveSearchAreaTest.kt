package com.artkachenko.livetextfinder.search

import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveSearchAreaTest {
    private val matrix = listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    @Test
    fun areaIsInclusiveAndRequiresEveryParticipantQuad() {
        val snapshot = OcrSnapshot(
            "inside crossing missing",
            listOf(
                TextLine(
                    "inside crossing missing",
                    null,
                    emptyList(),
                    listOf(
                        TextElement("inside", TextBounds(100, 40, 200, 80), emptyList()),
                        TextElement("crossing", TextBounds(190, 40, 210, 80), emptyList()),
                        TextElement("missing", null, emptyList()),
                    ),
                ),
            ),
            FrameInfo(1, 400, 200, 0, TextBounds(0, 0, 400, 200), matrix),
        )
        val state = OcrUiState(
            OcrStatus.TextRecognized,
            snapshot = snapshot,
            projection = FrameProjection(
                snapshot,
                FrameIdentity(1, 1),
                PreviewTarget(400, 200, matrix),
            ),
        )
        val search = searchRecognizedText("inside", state)
        val area = NormalizedSearchArea(0.25f, 0.2f, 0.5f, 0.4f)

        val inside = filterOccurrencesToArea(search, state.projection, area, 400, 200)
        assertEquals(1, inside!!.occurrences.size)
        val phrase = searchRecognizedText("inside crossing", state)
        val crossing = filterOccurrencesToArea(phrase, state.projection, area, 400, 200)
        assertTrue(crossing!!.occurrences.isEmpty())
        val missing = searchRecognizedText("missing", state)
        val incomplete = filterOccurrencesToArea(missing, state.projection, area, 400, 200)
        assertTrue(incomplete!!.occurrences.isEmpty())
        assertEquals(search, filterOccurrencesToArea(search, null, null, 0, 0))
        assertNull(filterOccurrencesToArea(search, state.projection, area, 0, 200))
    }

    @Test
    fun revisionRejectsStaleDraftEvenAfterFullFrameReset() {
        val owner = LiveSearchAreaOwner()
        val draftRevision = owner.revision
        owner.reset()
        assertFalse(owner.apply(draftRevision, NormalizedSearchArea.CENTERED_DEFAULT))
        assertTrue(owner.apply(owner.revision, NormalizedSearchArea.CENTERED_DEFAULT))
        assertEquals(NormalizedSearchArea.CENTERED_DEFAULT, owner.area)
        assertEquals(
            NormalizedSearchArea(0f, 0f, 1f, 1f),
            NormalizedSearchArea.fromSizeAndPosition(100, 100, 50, 50),
        )
    }

    @Test
    fun multiTermFilteringRequiresEveryParticipantAndInvalidMappingSuppressesCustomCounts() {
        val snapshot = OcrSnapshot(
            "alpha beta unknown",
            listOf(
                TextLine(
                    "alpha beta unknown",
                    null,
                    emptyList(),
                    listOf(
                        TextElement("alpha", TextBounds(100, 40, 150, 80), emptyList()),
                        TextElement("beta", TextBounds(190, 40, 210, 80), emptyList()),
                        TextElement("unknown", null, emptyList()),
                    ),
                ),
            ),
            FrameInfo(1, 400, 200, 0, TextBounds(0, 0, 400, 200), matrix),
        )
        val projection = FrameProjection(
            snapshot,
            FrameIdentity(2, 1),
            PreviewTarget(400, 200, matrix),
        )
        val state = OcrUiState(
            OcrStatus.TextRecognized,
            snapshot = snapshot,
            projection = projection,
            receivedAtMillis = 1_000,
        )
        val spec = ActiveSearchSpec(
            "list",
            "Three terms",
            listOf(
                SearchTerm("alpha", "alpha"),
                SearchTerm("beta", "beta"),
                SearchTerm("unknown", "unknown"),
            ),
        )
        val searched = searchRecognizedText(spec, state)
        val area = NormalizedSearchArea(0.25f, 0.2f, 0.5f, 0.5f)
        val filtered = filterOccurrencesToArea(searched, projection, area, 400, 200)
        assertEquals(listOf("alpha"), filtered!!.occurrences.map { it.termId })
        val incompletePhrase = searchRecognizedText(
            ActiveSearchSpec(
                "phrase",
                "Incomplete phrase",
                listOf(SearchTerm("phrase", "beta unknown")),
            ),
            state,
        )
        assertEquals(1, incompletePhrase.occurrences.size)
        assertEquals(listOf(1, 2), incompletePhrase.occurrences.single().elementIndices)
        val phraseArea = NormalizedSearchArea(0.25f, 0.2f, 0.75f, 0.5f)
        val betaSearch = searchRecognizedText("beta", state)
        val betaContained = assertNotNull(
            filterOccurrencesToArea(betaSearch, projection, phraseArea, 400, 200),
        )
        assertEquals(1, betaContained.occurrences.size)
        val phraseFiltered = assertNotNull(
            filterOccurrencesToArea(incompletePhrase, projection, phraseArea, 400, 200),
        )
        assertTrue(phraseFiltered.occurrences.isEmpty())

        val badTransform = projection.copy(
            target = PreviewTarget(400, 200, List(9) { 0f }),
        )
        assertNull(filterOccurrencesToArea(searched, badTransform, area, 400, 200))
        assertNull(filterOccurrencesToArea(searched, projection, area, 399, 200))
        assertEquals(3, searched.occurrences.size)
    }

    @Test
    fun freshnessAndAreaRevisionClearCustomCountsAndRetainedGeometry() {
        val owner = SearchPresentationOwner()
        val area = NormalizedSearchArea(0f, 0f, 1f, 1f)
        val visible = liveState(
            listOf(TextElement("word", TextBounds(100, 40, 140, 70), emptyList())),
            1_000,
        )
        val first = owner.present(manualSearchSpec("word"), visible, 1_000, area, 3, 400, 200)
        assertEquals(1, first.search.occurrences.size)
        assertTrue(first.outlinedElements.isNotEmpty())

        val missing = liveState(emptyList(), 1_100)
        val retained = owner.present(manualSearchSpec("word"), missing, 1_100, area, 3, 400, 200)
        assertTrue(retained.outlinedElements.isNotEmpty())
        val edited = owner.present(manualSearchSpec("word"), missing, 1_100, area, 4, 400, 200)
        assertTrue(edited.outlinedElements.isEmpty())
        assertFalse(isPresentationCurrentAreaRevision(retained, 4))
        assertTrue(isPresentationCurrentAreaRevision(edited, 4))

        val stale = liveState(
            listOf(TextElement("word", TextBounds(100, 40, 140, 70), emptyList())),
            1_000,
        )
        val notReady = SearchPresentationOwner().present(
            manualSearchSpec("word"),
            stale,
            2_500,
            area,
            0,
            400,
            200,
        )
        assertFalse(notReady.areaReady)
        assertTrue(notReady.search.occurrences.isEmpty())

        val legacy = SearchPresentationOwner().present(manualSearchSpec("word"), stale, 2_500)
        assertEquals(1, legacy.search.occurrences.size)
    }

    @Test
    fun mappingFailureClearsGraceAndSmoothingBeforeFreshRawGeometryReturns() {
        val owner = SearchPresentationOwner()
        val area = NormalizedSearchArea(0f, 0f, 1f, 1f)
        val original = owner.present(
            manualSearchSpec("word"),
            liveState(
                listOf(TextElement("word", TextBounds(100, 40, 140, 70), emptyList())),
                1_000,
            ),
            1_000,
            area,
            1,
            400,
            200,
        )
        val invalidState = liveState(emptyList(), 1_100).let { state ->
            state.copy(
                projection = state.projection?.copy(
                    target = PreviewTarget(400, 200, List(9) { 0f }),
                ),
            )
        }
        val waiting = owner.present(
            manualSearchSpec("word"),
            invalidState,
            1_100,
            area,
            1,
            400,
            200,
        )
        assertFalse(waiting.areaReady)
        assertTrue(waiting.outlinedElements.isEmpty())

        val recovered = owner.present(
            manualSearchSpec("word"),
            liveState(
                listOf(TextElement("word", TextBounds(103, 40, 143, 70), emptyList())),
                1_200,
            ),
            1_200,
            area,
            1,
            400,
            200,
        )
        assertTrue(original.outlinedElements.isNotEmpty())
        assertEquals(103f, recovered.outlinedElements.single().displayOutline.points.first().x)
    }

    @Test
    fun areaRevisionChangeClearsSmallShiftSmoothing() {
        val owner = SearchPresentationOwner()
        val area = NormalizedSearchArea(0f, 0f, 1f, 1f)
        owner.present(
            manualSearchSpec("word"),
            liveState(
                listOf(TextElement("word", TextBounds(100, 40, 140, 70), emptyList())),
                1_000,
            ),
            1_000,
            area,
            8,
            400,
            200,
        )
        val shifted = liveState(
            listOf(TextElement("word", TextBounds(103, 40, 143, 70), emptyList())),
            1_100,
        )
        val smoothed = owner.present(
            manualSearchSpec("word"),
            shifted,
            1_100,
            area,
            8,
            400,
            200,
        )
        assertEquals(101.5f, smoothed.outlinedElements.single().displayOutline.points.first().x)

        val afterEdit = owner.present(
            manualSearchSpec("word"),
            shifted,
            1_100,
            area,
            9,
            400,
            200,
        )
        assertEquals(103f, afterEdit.outlinedElements.single().displayOutline.points.first().x)
    }

    private fun liveState(elements: List<TextElement>, receivedAt: Long): OcrUiState {
        val text = elements.joinToString(" ") { it.text }
        val snapshot = OcrSnapshot(
            text,
            listOf(TextLine(text, null, emptyList(), elements)),
            FrameInfo(1, 400, 200, 0, TextBounds(0, 0, 400, 200), matrix),
        )
        return OcrUiState(
            OcrStatus.TextRecognized,
            snapshot = snapshot,
            projection = FrameProjection(
                snapshot,
                FrameIdentity(3, 1),
                PreviewTarget(400, 200, matrix),
            ),
            receivedAtMillis = receivedAt,
        )
    }
}
