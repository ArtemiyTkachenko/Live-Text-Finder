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
    fun editorSessionKeepsDraftsTemporaryAndRejectsStaleCancelApplyAndResetCallbacks() {
        val owner = LiveSearchAreaOwner()
        val committed = NormalizedSearchArea(0.1f, 0.1f, 0.9f, 0.9f)
        assertTrue(owner.apply(owner.revision, committed))
        val editor = LiveSearchAreaEditorSession(owner)
        val first = editor.begin(NormalizedSearchArea.CENTERED_DEFAULT)
        assertTrue(editor.update(first.token, null))
        assertEquals(committed, owner.area)
        assertEquals(null, editor.draft?.area)
        assertTrue(editor.cancel(first.token))
        assertEquals(committed, owner.area)

        val second = editor.begin(NormalizedSearchArea.CENTERED_DEFAULT)
        assertFalse(editor.cancel(first.token))
        assertFalse(editor.apply(first.token) { true })
        assertFalse(editor.apply(second.token) { false })
        assertEquals(committed, owner.area)
        assertTrue(editor.apply(second.token) { true })
        assertEquals(NormalizedSearchArea.CENTERED_DEFAULT, owner.area)

        val third = editor.begin(NormalizedSearchArea(0.3f, 0.3f, 0.7f, 0.7f))
        owner.reset()
        editor.cancelCurrent()
        val fourth = editor.begin(NormalizedSearchArea.CENTERED_DEFAULT)
        assertFalse(editor.apply(third.token) { true })
        assertEquals(NormalizedSearchArea.CENTERED_DEFAULT, editor.draft?.area)
        assertTrue(owner.area == null)
        assertEquals(fourth.token, editor.draft?.token)
    }

    @Test
    fun retainedPrecisionOpenerCannotReplaceReopenedDialogOrOpenAfterContextInvalidation() {
        val owner = LiveSearchAreaOwner()
        val editor = LiveSearchAreaEditorSession(owner)
        val dialogs = LiveSearchAreaDialogSession()
        var contextCurrent = true
        var opens = 0
        val open: (Long) -> Unit = {
            dialogs.open()
            opens++
        }
        val first = editor.begin(NormalizedSearchArea.CENTERED_DEFAULT)
        val oldAction = editor.precisionAction(first.token, { contextCurrent }, open)
        oldAction()
        assertEquals(1, opens)
        editor.cancel(first.token)
        val second = editor.begin(NormalizedSearchArea.CENTERED_DEFAULT)
        val currentAction = editor.precisionAction(second.token, { contextCurrent }, open)
        currentAction()
        val secondDialog = dialogs.currentToken
        oldAction()
        assertEquals(secondDialog, dialogs.currentToken)
        assertEquals(second.token, editor.draft?.token)
        assertEquals(2, opens)
        contextCurrent = false
        currentAction()
        assertEquals(secondDialog, dialogs.currentToken)
        assertEquals(2, opens)
        contextCurrent = true
        currentAction()
        assertEquals(3, opens)
        val currentDialog = dialogs.currentToken
        owner.reset()
        currentAction()
        assertEquals(currentDialog, dialogs.currentToken)
        assertEquals(3, opens)
        editor.cancelCurrent()
        currentAction()
        assertEquals(3, opens)
    }

    @Test
    fun directAreaMoveClampsAndPreservesDimensionsForNonSquarePreview() {
        val start = NormalizedSearchArea(0.2f, 0.25f, 0.7f, 0.65f)
        assertArea(NormalizedSearchArea(0.3f, 0f, 0.8f, 0.4f),
            moveSearchArea(start, 100f, -100f, 1000f, 250f))
        assertArea(NormalizedSearchArea(0.5f, 0.6f, 1f, 1f),
            moveSearchArea(start, 900f, 900f, 1000f, 250f))
        assertNull(moveSearchArea(start, 1f, 1f, 0f, 250f))
        assertNull(moveSearchArea(start, Float.NaN, 1f, 100f, 100f))
    }

    @Test
    fun directAreaResizeAnchorsOppositeCornerAndEnforcesMinimumWithoutFlipping() {
        val start = NormalizedSearchArea(0.2f, 0.2f, 0.8f, 0.8f)
        assertArea(NormalizedSearchArea(0.3f, 0.3f, 0.8f, 0.8f),
            resizeSearchArea(start, SearchAreaCorner.TopLeft, 100f, 50f, 1000f, 500f))
        assertArea(NormalizedSearchArea(0.2f, 0.2f, 1f, 0.8f),
            resizeSearchArea(start, SearchAreaCorner.TopRight, 500f, 0f, 1000f, 500f))
        assertArea(NormalizedSearchArea(0.6f, 0.2f, 0.8f, 0.4f),
            resizeSearchArea(start, SearchAreaCorner.BottomLeft, 900f, -900f, 1000f, 500f))
        assertArea(NormalizedSearchArea(0.2f, 0.2f, 0.4f, 0.4f),
            resizeSearchArea(start, SearchAreaCorner.BottomRight, -900f, -900f, 1000f, 500f))
        assertNull(resizeSearchArea(start, SearchAreaCorner.BottomRight, 1f, 1f, 0f, 500f))
    }

    @Test
    fun cornerHitTargetsClampInwardAtEdgesAndChooseNearestDeterministicallyWhenOverlapping() {
        val fullFrame = NormalizedSearchArea(0f, 0f, 1f, 1f)
        assertEquals(
            SearchAreaCorner.TopLeft,
            searchAreaCornerAt(fullFrame, 24f, 24f, 400f, 400f, 48f),
        )
        assertEquals(
            SearchAreaCorner.TopLeft,
            searchAreaCornerAt(NormalizedSearchArea.CENTERED_DEFAULT, 20f, 20f, 40f, 40f, 48f),
        )
        assertNull(searchAreaCornerAt(fullFrame, 24f, 24f, 0f, 400f, 48f))
    }

    @Test
    fun cornerAccessibilityStepsGrowAndShrinkEachEdgeWhileKeepingOppositeEdgeAnchored() {
        val start = NormalizedSearchArea(0.2f, 0.2f, 0.8f, 0.8f)
        SearchAreaCorner.values().forEach { corner ->
            listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).forEach { (dx, dy) ->
                val growHorizontal = dx < 0 &&
                    (corner == SearchAreaCorner.TopLeft ||
                        corner == SearchAreaCorner.BottomLeft) || dx > 0 &&
                    (corner == SearchAreaCorner.TopRight ||
                        corner == SearchAreaCorner.BottomRight)
                val growVertical = dy < 0 &&
                    (corner == SearchAreaCorner.TopLeft ||
                        corner == SearchAreaCorner.TopRight) || dy > 0 &&
                    (corner == SearchAreaCorner.BottomLeft ||
                        corner == SearchAreaCorner.BottomRight)
                val expected = when {
                    dx != 0 && corner == SearchAreaCorner.TopLeft -> start.copy(
                        left = if (growHorizontal) 0.15f else 0.25f,
                    )
                    dx != 0 && corner == SearchAreaCorner.BottomLeft -> start.copy(
                        left = if (growHorizontal) 0.15f else 0.25f,
                    )
                    dx != 0 && corner == SearchAreaCorner.TopRight -> start.copy(
                        right = if (growHorizontal) 0.85f else 0.75f,
                    )
                    dx != 0 -> start.copy(right = if (growHorizontal) 0.85f else 0.75f)
                    dy != 0 && corner == SearchAreaCorner.TopLeft -> start.copy(
                        top = if (growVertical) 0.15f else 0.25f,
                    )
                    dy != 0 && corner == SearchAreaCorner.TopRight -> start.copy(
                        top = if (growVertical) 0.15f else 0.25f,
                    )
                    dy != 0 && corner == SearchAreaCorner.BottomLeft -> start.copy(
                        bottom = if (growVertical) 0.85f else 0.75f,
                    )
                    else -> start.copy(bottom = if (growVertical) 0.85f else 0.75f)
                }
                assertArea(
                    expected,
                    resizeSearchAreaByStep(start, corner, dx, dy, 100f, 100f),
                )
            }
        }
        assertArea(
            NormalizedSearchArea(0f, 0f, 1f, 1f),
            resizeSearchAreaByStep(
                NormalizedSearchArea(0f, 0f, 1f, 1f),
                SearchAreaCorner.TopLeft,
                -1,
                -1,
                100f,
                100f,
            ),
        )
    }

    @Test
    fun precisionFullFrameResultRemainsNullDraftForOuterApply() {
        assertNull(normalizeEditorDraftArea(NormalizedSearchArea(0f, 0f, 1f, 1f)))
        val custom = NormalizedSearchArea(0.1f, 0.1f, 0.9f, 0.9f)
        assertEquals(custom, normalizeEditorDraftArea(custom))
        assertNull(normalizeEditorDraftArea(null))
    }

    @Test
    fun precisionDialogSessionRejectsStaleApplyDismissAndOuterEditorCallbacks() {
        val owner = LiveSearchAreaOwner()
        val committed = NormalizedSearchArea(0.1f, 0.1f, 0.9f, 0.9f)
        owner.apply(owner.revision, committed)
        val editor = LiveSearchAreaEditorSession(owner)
        val outer = editor.begin(NormalizedSearchArea.CENTERED_DEFAULT)
        val dialogs = LiveSearchAreaDialogSession()
        val first = dialogs.open()
        assertTrue(dialogs.dismiss(first) {})
        val reopenedInSameEditor = dialogs.open()
        assertFalse(dialogs.dispatch(first) { editor.update(outer.token, null) })
        assertFalse(dialogs.dismiss(first) { editor.cancel(outer.token) })
        assertEquals(reopenedInSameEditor, dialogs.currentToken)
        assertEquals(NormalizedSearchArea.CENTERED_DEFAULT, editor.draft?.area)
        assertTrue(dialogs.dispatch(reopenedInSameEditor) {
            editor.update(outer.token, null)
        })
        assertEquals(committed, owner.area)

        assertTrue(editor.cancel(outer.token))
        dialogs.invalidate()
        val nextOuter = editor.begin(NormalizedSearchArea(0.3f, 0.3f, 0.7f, 0.7f))
        val nextDialog = dialogs.open()
        assertFalse(dialogs.dispatch(reopenedInSameEditor) {
            editor.update(outer.token, NormalizedSearchArea.CENTERED_DEFAULT)
        })
        assertTrue(dialogs.dispatch(nextDialog) {
            editor.update(nextOuter.token, NormalizedSearchArea.CENTERED_DEFAULT)
        })
        assertEquals(NormalizedSearchArea.CENTERED_DEFAULT, editor.draft?.area)
        assertEquals(committed, owner.area)
        assertTrue(editor.cancel(nextOuter.token))

        val standaloneFirst = dialogs.open()
        assertTrue(dialogs.dismiss(standaloneFirst) {})
        val standaloneReopen = dialogs.open()
        assertFalse(dialogs.dispatch(standaloneFirst) {
            owner.apply(owner.revision, null)
        })
        assertFalse(dialogs.dismiss(standaloneFirst) { owner.reset() })
        assertEquals(standaloneReopen, dialogs.currentToken)
        assertEquals(committed, owner.area)
        assertTrue(dialogs.dispatch(standaloneReopen) {
            owner.apply(owner.revision, null)
        })
        assertNull(owner.area)
    }

    private fun assertArea(expected: NormalizedSearchArea, actual: NormalizedSearchArea?) {
        assertNotNull(actual)
        assertEquals(expected.left, actual.left, 0.0001f)
        assertEquals(expected.top, actual.top, 0.0001f)
        assertEquals(expected.right, actual.right, 0.0001f)
        assertEquals(expected.bottom, actual.bottom, 0.0001f)
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
