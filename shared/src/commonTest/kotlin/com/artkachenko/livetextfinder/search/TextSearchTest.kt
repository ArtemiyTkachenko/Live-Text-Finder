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
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TextSearchTest {
    @Test fun emptyAndWhitespaceQueriesNeverMatch() {
        val snapshot = snapshot(listOf("AB-123"))
        listOf("", " ", "\t\n\u00A0").forEach { query ->
            val result = search(query, snapshot)
            assertEquals("", result.query)
            assertEquals(emptyList(), result.occurrences)
            assertSame(snapshot, result.snapshot)
        }
    }

    @Test fun caseInsensitiveIdentifierSubstringRetainsFrameAndOriginalGeometry() {
        val snapshot = snapshot(listOf("AB-123", "part"))
        val result = search("ab-1", snapshot)
        assertEquals(listOf(TextOccurrence(0, 0, 4, listOf(0))), result.occurrences)
        assertSame(snapshot, result.snapshot)
        assertEquals(42L, result.snapshot?.frame?.timestampNanos)
        assertSame(snapshot.lines[0].elements[0], result.snapshot?.lines?.get(0)?.elements?.get(0))
        assertEquals(TextBounds(0, 0, 100, 20), result.snapshot?.lines?.get(0)?.elements?.get(0)?.bounds)
        assertEquals(listOf(TextPoint(0, 0)), snapshot.lines[0].elements[0].corners)
        assertEquals("AB-123", snapshot.lines[0].elements[0].text)
    }

    @Test fun punctuationAccentsAndHyphensRemainLiteral() {
        val snapshot = snapshot(listOf("Café", "AB-123", "a.b"))
        assertEquals(listOf(TextOccurrence(0, 0, 4, listOf(0))), search("CAFÉ", snapshot).occurrences)
        assertTrue(search("cafe", snapshot).occurrences.isEmpty())
        assertTrue(search("ab123", snapshot).occurrences.isEmpty())
        assertTrue(search("a?b", snapshot).occurrences.isEmpty())
        assertEquals(listOf(TextOccurrence(0, 12, 15, listOf(2))), search("A.B", snapshot).occurrences)
    }

    @Test fun caseComparisonDoesNotExpandCharactersOrFoldAccents() {
        val snapshot = snapshot(listOf("Straße", "İ", "café"))
        assertTrue(search("STRASSE", snapshot).occurrences.isEmpty())
        assertEquals(listOf(TextOccurrence(0, 4, 5, listOf(0))), search("ß", snapshot).occurrences)
        assertEquals(listOf(TextOccurrence(0, 7, 8, listOf(1))), search("i", snapshot).occurrences)
        assertTrue(search("cafe\u0301", snapshot).occurrences.isEmpty())
    }

    @Test fun queryAndElementsNormalizeWhitespaceWithoutLosingElementIndices() {
        val snapshot = snapshot(listOf(" ", "  Alpha\t\nBeta ", "\u00A0", " Gamma  "))
        val result = search(" \tBETA\n\u00A0gamma  ", snapshot)
        assertEquals("BETA gamma", result.query)
        assertEquals(listOf(TextOccurrence(0, 6, 16, listOf(1, 3))), result.occurrences)
        assertEquals(listOf(" ", "  Alpha\t\nBeta ", "\u00A0", " Gamma  "), snapshot.lines[0].elements.map { it.text })
    }

    @Test fun phraseSpansElementsWithinOneLine() {
        val snapshot = snapshot(listOf("AB-123", "part"))
        assertEquals(listOf(TextOccurrence(0, 3, 11, listOf(0, 1))), search("123 part", snapshot).occurrences)
    }

    @Test fun phraseNeverCrossesLinesAndLinesWithoutElementsDoNotMatch() {
        val snapshot = snapshot(listOf("foo"), listOf("bar"), emptyList())
        assertTrue(search("foo bar", snapshot).occurrences.isEmpty())
        assertTrue(search("diagnostic", snapshot).occurrences.isEmpty())
    }

    @Test fun repeatedOccurrencesAreCountedWithoutOverlapAndKeepSameElementIdentity() {
        val snapshot = snapshot(listOf("banana", "foofoo"))
        assertEquals(listOf(TextOccurrence(0, 1, 4, listOf(0))), search("ana", snapshot).occurrences)
        assertEquals(
            listOf(TextOccurrence(0, 7, 10, listOf(1)), TextOccurrence(0, 10, 13, listOf(1))),
            search("foo", snapshot).occurrences,
        )
    }

    @Test fun duplicatesOnDifferentLinesKeepDistinctLineIndices() {
        val snapshot = snapshot(listOf("foo"), listOf("foo"))
        assertEquals(
            listOf(TextOccurrence(0, 0, 3, listOf(0)), TextOccurrence(1, 0, 3, listOf(0))),
            search("foo", snapshot).occurrences,
        )
    }

    @Test fun missingGeometryDoesNotDiscardRecognizedOccurrences() {
        val original = snapshot(listOf("foo", "bar"))
        val line = original.lines[0]
        val snapshot = original.copy(lines = listOf(line.copy(elements = line.elements.map {
            it.copy(bounds = null, corners = emptyList())
        })))
        val result = search("foo bar", snapshot)
        assertEquals(listOf(TextOccurrence(0, 0, 7, listOf(0, 1))), result.occurrences)
        assertSame(snapshot, result.snapshot)
        assertNull(result.snapshot?.lines?.get(0)?.elements?.get(0)?.bounds)
    }

    @Test fun aggregateDiagnosticTextIsNotSearchedOrCountedTwice() {
        val original = snapshot(listOf("foo"))
        val snapshot = original.copy(text = "phantom foo foo", lines = listOf(original.lines[0].copy(text = "phantom foo")))
        assertTrue(search("phantom", snapshot).occurrences.isEmpty())
        assertEquals(listOf(TextOccurrence(0, 0, 3, listOf(0))), search("foo", snapshot).occurrences)
    }

    @Test fun queryEditsClearingAndSnapshotReplacementProduceNewResults() {
        val first = snapshot(listOf("foofoo"))
        val next = snapshot(listOf("bar")).copy(frame = first.frame.copy(timestampNanos = 99))
        assertEquals(2, search("foo", first).occurrences.size)
        assertTrue(search("bar", first).occurrences.isEmpty())
        assertTrue(search("", first).occurrences.isEmpty())
        val replacement = search("bar", next)
        assertEquals(listOf(TextOccurrence(0, 0, 3, listOf(0))), replacement.occurrences)
        assertSame(next, replacement.snapshot)
        assertEquals(99L, replacement.snapshot?.frame?.timestampNanos)
        assertTrue(search("foo", next).occurrences.isEmpty())
        val blank = snapshot().copy(frame = first.frame.copy(timestampNanos = 100))
        val cleared = searchRecognizedText("foo", OcrUiState(OcrStatus.Scanning, snapshot = blank))
        assertTrue(cleared.occurrences.isEmpty())
        assertSame(blank, cleared.snapshot)
        assertEquals(100L, cleared.snapshot?.frame?.timestampNanos)
    }

    @Test
    fun independentTermsMayOverlapAndPhraseTermsStayWithinOneLine() {
        val snapshot = snapshot(
            listOf("infoobar"),
            listOf("new"),
            listOf("york"),
            listOf("new york"),
        )
        val spec = ActiveSearchSpec(
            "list-1",
            "Parts",
            listOf(
                SearchTerm("t0", "foo"),
                SearchTerm("t1", "foobar"),
                SearchTerm("t2", "new york"),
            ),
        )
        val result = searchRecognizedText(
            spec,
            OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot),
        )
        assertEquals(
            listOf(
                TextOccurrence(0, 2, 5, listOf(0), "t0"),
                TextOccurrence(0, 2, 8, listOf(0), "t1"),
                TextOccurrence(3, 0, 8, listOf(0), "t2"),
            ),
            result.occurrences,
        )
        assertEquals(3, result.occurrences.size)
    }

    @Test fun onlyCurrentScanningAndRecognizedSnapshotsAreEligible() {
        val snapshot = snapshot(listOf("foo"))
        OcrStatus.entries.forEach { status ->
            val result = searchRecognizedText("foo", OcrUiState(status, snapshot = snapshot))
            if (status == OcrStatus.Scanning || status == OcrStatus.TextRecognized) {
                assertEquals(listOf(TextOccurrence(0, 0, 3, listOf(0))), result.occurrences)
                assertSame(snapshot, result.snapshot)
            } else {
                assertTrue(result.occurrences.isEmpty(), status.name)
                assertNull(result.snapshot, status.name)
            }
            assertTrue(searchRecognizedText("foo", OcrUiState(status)).occurrences.isEmpty())
        }
    }

    private fun search(query: String, snapshot: OcrSnapshot) =
        searchRecognizedText(query, OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot))

    private fun snapshot(vararg lines: List<String>) = OcrSnapshot(
        text = "diagnostic aggregate",
        lines = lines.map { elements ->
            TextLine("diagnostic line", null, emptyList(), elements.map { text ->
                TextElement(text, TextBounds(0, 0, 100, 20), listOf(TextPoint(0, 0)))
            })
        },
        frame = FrameInfo(42, 1280, 720, 0, TextBounds(0, 0, 1280, 720), emptyList()),
    )
}
