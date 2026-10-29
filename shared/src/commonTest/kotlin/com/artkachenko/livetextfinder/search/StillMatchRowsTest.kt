package com.artkachenko.livetextfinder.search

import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import com.artkachenko.livetextfinder.ocr.TextPoint
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StillMatchRowsTest {
    @Test
    fun rowsPreserveMixedCaseRepeatedAndOverlappingTermOccurrences() {
        val content = snapshot(listOf("ababa", "ABaBa"))
        val result = searchRecognizedText(
            ActiveSearchSpec(
                identity = "overlap",
                displayName = "Overlapping",
                terms = listOf(SearchTerm("short", "aba"), SearchTerm("long", "ababa")),
            ),
            OcrUiState(OcrStatus.TextRecognized, snapshot = content),
        )

        val rows = stillMatchRows(result)
        assertEquals(
            listOf("aba", "ABa", "ababa", "ABaBa"),
            rows.map { it.matchedText },
        )
        assertEquals(listOf("short", "short", "long", "long"), rows.map { it.occurrence.termId })
        assertEquals(listOf(0, 0, 0, 0), rows.map { it.occurrence.lineIndex })
        assertEquals(listOf(0, 6, 0, 6), rows.map { it.occurrence.start })
        assertEquals("aba", stillMatchText(rows.first(), result))
    }

    @Test
    fun rowsAndPayloadRejectMalformedOccurrenceBoundsAndChangedPayload() {
        val content = snapshot(listOf("MATCH"))
        val result = searchRecognizedText(
            "MATCH",
            OcrUiState(OcrStatus.TextRecognized, snapshot = content),
        )
        val occurrence = result.occurrences.single()
        val malformed = result.copy(
            occurrences = listOf(
                occurrence.copy(start = -1),
                occurrence.copy(endExclusive = 99),
                occurrence.copy(start = 3, endExclusive = 3),
                occurrence.copy(lineIndex = 4),
            ),
        )

        assertTrue(stillMatchRows(malformed).isEmpty())
        val tamperedRow = stillMatchRows(result).single().copy(matchedText = "other")
        assertNull(stillMatchText(tamperedRow, result))
    }

    @Test
    fun rowsUseNormalizedElementTextAndKeepOccurrenceOrder() {
        val snapshot = snapshot(listOf("  AB-", "123", "part ", "AB-123"))
        val spec = ActiveSearchSpec(
            identity = "search-7",
            displayName = "Parts",
            terms = listOf(SearchTerm("phrase", "AB- 123"), SearchTerm("sku", "AB-123")),
        )
        val result = searchRecognizedText(
            spec,
            OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot),
        )

        assertEquals(listOf("AB- 123", "AB-123"), stillMatchRows(result).map { it.matchedText })
        assertEquals(
            listOf("AB- 123 part AB-123", "AB- 123 part AB-123"),
            stillMatchRows(result).map { it.lineContext },
        )
        assertEquals(listOf(0, 0), stillMatchRows(result).map { it.occurrence.lineIndex })
        assertEquals("AB- 123", stillMatchText(stillMatchRows(result).first(), result))
    }

    @Test
    fun copyWritesExactSelectedOccurrenceOnlyWhileSourceSearchAndSelectionAreCurrent() {
        val firstContent = snapshot(listOf("prefix", "  MATCH", "ED suffix"))
        val result = searchRecognizedText(
            "MATCH ED",
            OcrUiState(OcrStatus.TextRecognized, snapshot = firstContent),
        )
        val row = stillMatchRows(result).single()
        val ownerToken = Any()
        val source = StillMatchSourceState("frozen:request-2", firstContent, ownerToken)
        var currentSource = source
        var currentSearch = result.spec.identity
        var currentSelection: Int? = row.index
        var copied = mutableListOf<String>()

        val success = runSuspend {
            copyStillMatch(
                row,
                result,
                "frozen:request-2",
                result.spec.identity,
                row.index,
                ownerToken,
                { currentSource },
                { currentSearch },
                { currentSelection },
                { copied.add(it) },
            )
        }

        assertTrue(success)
        assertEquals(listOf("MATCH ED"), copied)

        currentSource = source.copy(ownerToken = Any())
        copied = mutableListOf()
        assertFalse(
            runSuspend {
                copy(
                    result,
                    row,
                    ownerToken,
                    { currentSource },
                    { currentSearch },
                    { currentSelection },
                ) { copied.add(it) }
            },
        )
        assertTrue(copied.isEmpty())

        currentSource = source
        currentSource = StillMatchSourceState(null, null, null)
        assertFalse(
            runSuspend {
                copy(
                    result,
                    row,
                    ownerToken,
                    { currentSource },
                    { currentSearch },
                    { currentSelection },
                ) { copied.add(it) }
            },
        )
        assertTrue(copied.isEmpty())

        currentSource = source
        currentSearch = "search-replaced"
        assertFalse(
            runSuspend {
                copy(
                    result,
                    row,
                    ownerToken,
                    { currentSource },
                    { currentSearch },
                    { currentSelection },
                ) { copied.add(it) }
            },
        )
        currentSearch = result.spec.identity

        currentSelection = null
        assertFalse(
            runSuspend {
                copy(
                    result,
                    row,
                    ownerToken,
                    { currentSource },
                    { currentSearch },
                    { currentSelection },
                ) { copied.add(it) }
            },
        )
        assertTrue(copied.isEmpty())
    }

    @Test
    fun copyRejectsWrongOccurrenceAndContentSnapshotEvenWithMatchingSourceId() {
        val content = snapshot(listOf("MATCH", "MATCH"))
        val result = searchRecognizedText(
            "MATCH",
            OcrUiState(OcrStatus.TextRecognized, snapshot = content),
        )
        val rows = stillMatchRows(result)
        val ownerToken = Any()
        val currentSource = StillMatchSourceState("same-id", content, ownerToken)
        var copied = mutableListOf<String>()

        assertFalse(
            runSuspend {
                copyStillMatch(
                    rows[0],
                    result,
                    "same-id",
                    result.spec.identity,
                    rows[1].index,
                    ownerToken,
                    { currentSource },
                    { result.spec.identity },
                    { rows[1].index },
                    { copied.add(it) },
                )
            },
        )
        val replacedContent = snapshot(listOf("MATCH", "MATCH"))
        assertFalse(
            runSuspend {
                copyStillMatch(
                    rows[0], result, "same-id", result.spec.identity, rows[0].index, ownerToken,
                    { StillMatchSourceState("same-id", replacedContent, ownerToken) },
                    { result.spec.identity }, { rows[0].index }, { copied.add(it) },
                )
            },
        )
        assertTrue(copied.isEmpty())
        assertNull(stillMatchText(rows[0].copy(index = 9), result))
    }

    private suspend fun copy(
        result: TextSearchResult,
        row: StillMatchRow,
        ownerToken: Any,
        currentSource: () -> StillMatchSourceState,
        currentSearch: () -> String,
        currentSelection: () -> Int?,
        writeText: suspend (String) -> Unit,
    ) = copyStillMatch(
        row,
        result,
        "frozen:request-2",
        result.spec.identity,
        row.index,
        ownerToken,
        currentSource,
        currentSearch,
        currentSelection,
        writeText,
    )

    private fun snapshot(vararg lines: List<String>) = OcrSnapshot(
        text = "aggregate diagnostic text must not be used",
        lines = lines.map { elements ->
            TextLine(
                "diagnostic line",
                null,
                emptyList(),
                elements.map { text ->
                    TextElement(text, TextBounds(0, 0, 100, 20), listOf(TextPoint(0, 0)))
                },
            )
        },
        frame = FrameInfo(1, 100, 100, 0, TextBounds(0, 0, 100, 100), emptyList()),
    )

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        })
        return requireNotNull(outcome).getOrThrow()
    }
}
