package com.artkachenko.livetextfinder.search

import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import com.artkachenko.livetextfinder.ocr.TextPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FrozenHighlightsTest {
    @Test
    fun asymmetricElementFollowsCapturedRotationAndLetterboxAtEveryQuarterTurn() {
        val expected = mapOf(
            0 to listOf(2.5f to 7.5f, 7.5f to 7.5f, 7.5f to 10f, 2.5f to 10f),
            90 to listOf(12.5f to 2.5f, 12.5f to 7.5f, 10f to 7.5f, 10f to 2.5f),
            180 to listOf(17.5f to 12.5f, 12.5f to 12.5f, 12.5f to 10f, 17.5f to 10f),
            270 to listOf(7.5f to 17.5f, 7.5f to 12.5f, 10f to 12.5f, 10f to 17.5f),
        )
        expected.forEach { (rotation, coordinates) ->
            val snapshot = snapshot(rotation)
            val search = searchRecognizedText("foo", recognized(snapshot))

            val projected = projectFrozenHighlights(search, 20f, 20f)

            assertTrue(projected.ready)
            assertSame(snapshot, search.snapshot)
            assertEquals(
                coordinates.map { (x, y) -> PreviewPoint(x, y) },
                projected.outlines.single().points,
            )
        }
    }

    @Test
    fun overlappingFrozenTermsCountIndependentlyAndShareOneSourceOutline() {
        val snapshot = snapshot(0)
        val search = searchRecognizedText(
            ActiveSearchSpec(
                "list:parts",
                "Parts",
                listOf(SearchTerm("short", "foo"), SearchTerm("long", "foobar")),
            ),
            recognized(snapshot),
        )

        val projected = projectFrozenHighlights(search, 40f, 10f)

        assertEquals(2, search.occurrences.size)
        assertEquals(listOf("short", "long"), search.occurrences.map { it.termId })
        assertEquals(1, projected.outlines.size)
        assertEquals(0, projected.outlines.single().lineIndex)
        assertEquals(0, projected.outlines.single().elementIndex)
        assertEquals(
            listOf(12.5f to 2.5f, 17.5f to 2.5f, 17.5f to 5f, 12.5f to 5f)
                .map { (x, y) -> PreviewPoint(x, y) },
            projected.outlines.single().points,
        )
    }

    @Test
    fun frozenQueryRecomputationNeedsNoLiveReceiptOrPreviewProjection() {
        val snapshot = snapshot(0)
        val state = recognized(snapshot)
        val found = searchRecognizedText("foo", state)
        val changed = searchRecognizedText("missing", state)
        val cleared = searchRecognizedText("", state)

        assertTrue(projectFrozenHighlights(found, 20f, 20f).ready)
        assertEquals(1, projectFrozenHighlights(found, 20f, 20f).outlines.size)
        assertTrue(projectFrozenHighlights(changed, 20f, 20f).outlines.isEmpty())
        assertTrue(projectFrozenHighlights(cleared, 20f, 20f).outlines.isEmpty())
        assertSame(snapshot, changed.snapshot)
        assertFalse(projectHighlights(found, null, 20, 20).ready)
    }

    @Test
    fun blankSnapshotIsValidButInvalidViewportCannotDraw() {
        val blank = snapshot(0).copy(text = "", lines = emptyList())
        val search = searchRecognizedText("foo", recognized(blank))

        val projected = projectFrozenHighlights(search, 20f, 20f)

        assertTrue(projected.ready)
        assertTrue(projected.outlines.isEmpty())
        assertFalse(projectFrozenHighlights(search, 0f, 20f).ready)
        assertFalse(projectFrozenHighlights(search, Float.NaN, 20f).ready)
    }

    private fun recognized(snapshot: OcrSnapshot): OcrUiState =
        OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot)

    private fun snapshot(rotation: Int): OcrSnapshot {
        // These are already-upright OCR corners for the raw rectangle (1,1)..(3,2).
        val corners = when (rotation) {
            90 -> listOf(TextPoint(3, 1), TextPoint(3, 3), TextPoint(2, 3), TextPoint(2, 1))
            180 -> listOf(TextPoint(7, 3), TextPoint(5, 3), TextPoint(5, 2), TextPoint(7, 2))
            270 -> listOf(TextPoint(1, 7), TextPoint(1, 5), TextPoint(2, 5), TextPoint(2, 7))
            else -> listOf(TextPoint(1, 1), TextPoint(3, 1), TextPoint(3, 2), TextPoint(1, 2))
        }
        val element = TextElement("foobar", null, corners)
        return OcrSnapshot(
            text = "foobar",
            lines = listOf(TextLine("foobar", null, emptyList(), listOf(element))),
            frame = FrameInfo(
                timestampNanos = 0L,
                bufferWidth = 8,
                bufferHeight = 4,
                rotationDegrees = rotation,
                crop = TextBounds(1, 1, 7, 3),
                sensorToBufferMatrix = emptyList(),
            ),
        )
    }
}
