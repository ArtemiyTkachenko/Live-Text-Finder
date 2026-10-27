package com.artkachenko.livetextfinder.search

import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.ImportedImageState
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import com.artkachenko.livetextfinder.ocr.TextPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ImportedHighlightsTest {
    @Test
    fun importedSearchAndOutlineUseStillGeometryWithoutCameraMetadata() {
        val snapshot = importedSnapshot()
        val search = searchRecognizedContent(manualSearchSpec("target"), snapshot)

        val highlights = projectImportedHighlights(search, snapshot, 20f, 20f)

        assertEquals(listOf(TextOccurrence(0, 0, 6, listOf(0))), search.occurrences)
        assertSame(snapshot, search.content)
        assertNull(search.snapshot)
        assertTrue(highlights.ready)
        assertEquals(
            listOf(PreviewPoint(2.5f, 7.5f), PreviewPoint(7.5f, 7.5f),
                PreviewPoint(7.5f, 10f), PreviewPoint(2.5f, 10f)),
            highlights.outlines.single().points,
        )
        assertFalse(projectFrozenHighlights(search, 20f, 20f).ready)
    }

    @Test
    fun importedOutlinesRejectStaleSourceAndInvalidViewport() {
        val snapshot = importedSnapshot()
        val search = searchRecognizedContent(manualSearchSpec("target"), snapshot)

        assertFalse(
            projectImportedHighlights(search, importedSnapshot(12L), 20f, 20f).ready,
        )
        assertFalse(projectImportedHighlights(search, snapshot, 0f, 20f).ready)
    }

    @Test
    fun drawEligibilityTracksEditsClearListTermsCurrentViewportAndSource() {
        val snapshot = importedListSnapshot()
        val listSpec = ActiveSearchSpec(
            identity = "saved-list-7",
            displayName = "Two terms",
            terms = listOf(SearchTerm("one", "alpha"), SearchTerm("two", "beta")),
        )
        val listSearch = searchRecognizedContent(listSpec, snapshot)
        val imported = ImportedImageState(
            phase = ImportedImagePhase.Imported,
            requestId = 4L,
            snapshot = snapshot,
        )

        val listDraw = importedHighlightsForDraw(
            listSearch,
            snapshot,
            imported,
            listSpec.identity,
            listSpec.identity,
            80f,
            48f,
            contentVisible = true,
        )
        assertTrue(listDraw.ready)
        assertEquals(2, listSearch.occurrences.size)
        assertEquals(1, listDraw.outlines.size)

        val edited = searchRecognizedContent(manualSearchSpec("missing"), snapshot)
        val staleQuery = importedHighlightsForDraw(
            listSearch,
            snapshot,
            imported,
            listSpec.identity,
            edited.spec.identity,
            80f,
            48f,
            contentVisible = true,
        )
        assertFalse(staleQuery.ready)
        assertTrue(staleQuery.outlines.isEmpty())

        val cleared = searchRecognizedContent(manualSearchSpec(""), snapshot)
        val clearedDraw = importedHighlightsForDraw(
            cleared,
            snapshot,
            imported,
            cleared.spec.identity,
            cleared.spec.identity,
            80f,
            48f,
            contentVisible = true,
        )
        assertTrue(clearedDraw.ready)
        assertTrue(clearedDraw.outlines.isEmpty())

        val portrait = importedHighlightsForDraw(
            listSearch,
            snapshot,
            imported,
            listSpec.identity,
            listSpec.identity,
            48f,
            80f,
            contentVisible = true,
        )
        assertTrue(portrait.ready)
        assertEquals(1, portrait.outlines.size)
        assertTrue(portrait.outlines.single().points.first().y >
            listDraw.outlines.single().points.first().y)

        val otherSource = importedListSnapshot(sourceIdentity = 92L)
        val staleSource = importedHighlightsForDraw(
            listSearch,
            otherSource,
            imported,
            listSpec.identity,
            listSpec.identity,
            80f,
            48f,
            contentVisible = true,
        )
        assertFalse(staleSource.ready)
        assertTrue(staleSource.outlines.isEmpty())
        val hidden = importedHighlightsForDraw(
            listSearch,
            snapshot,
            imported,
            listSpec.identity,
            listSpec.identity,
            80f,
            48f,
            contentVisible = false,
        )
        assertFalse(hidden.ready)
        assertTrue(hidden.outlines.isEmpty())
    }

    private fun importedSnapshot(sourceIdentity: Long = 11L) = ImportedTextSnapshot(
        text = "target",
        lines = listOf(
            TextLine(
                "target",
                null,
                emptyList(),
                listOf(
                    TextElement(
                        "target",
                        TextBounds(1, 1, 3, 2),
                        listOf(
                            TextPoint(1, 1),
                            TextPoint(3, 1),
                            TextPoint(3, 2),
                            TextPoint(1, 2),
                        ),
                    ),
                ),
            ),
        ),
        width = 8,
        height = 4,
        sourceIdentity = sourceIdentity,
    )

    private fun importedListSnapshot(sourceIdentity: Long = 91L) = ImportedTextSnapshot(
        text = "alpha beta",
        lines = listOf(
            TextLine(
                "alpha beta",
                null,
                emptyList(),
                listOf(
                    TextElement(
                        "alpha beta",
                        TextBounds(20, 12, 60, 30),
                        listOf(
                            TextPoint(20, 12), TextPoint(60, 12),
                            TextPoint(60, 30), TextPoint(20, 30),
                        ),
                    ),
                ),
            ),
        ),
        width = 80,
        height = 48,
        sourceIdentity = sourceIdentity,
    )
}
