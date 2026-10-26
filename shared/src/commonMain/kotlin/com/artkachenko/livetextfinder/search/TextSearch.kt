package com.artkachenko.livetextfinder.search

import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState

/** Offsets refer to the normalized line, not character boxes or preview coordinates. */
internal data class TextOccurrence(
    val lineIndex: Int,
    val start: Int,
    val endExclusive: Int,
    val elementIndices: List<Int>,
)

/** Original snapshot retains frame metadata and geometry for every referenced element. */
internal data class TextSearchResult(
    val query: String,
    val snapshot: OcrSnapshot?,
    val occurrences: List<TextOccurrence>,
)

/** Pure replacement result: inactive states deliberately discard any retained snapshot. */
internal fun searchRecognizedText(query: String, state: OcrUiState): TextSearchResult {
    val normalizedQuery = normalizeWhitespace(query)
    val snapshot = state.snapshot.takeIf {
        state.status == OcrStatus.TextRecognized || state.status == OcrStatus.Scanning
    }
    if (snapshot == null || normalizedQuery.isEmpty()) {
        return TextSearchResult(normalizedQuery, snapshot, emptyList())
    }
    val occurrences = buildList {
        snapshot.lines.forEachIndexed { lineIndex, line ->
            val content = StringBuilder()
            val spans = buildList {
                line.elements.forEachIndexed { index, element ->
                    val text = normalizeWhitespace(element.text)
                    if (text.isNotEmpty()) {
                        if (content.isNotEmpty()) content.append(' ')
                        val start = content.length
                        content.append(text)
                        add(ElementSpan(index, start, content.length))
                    }
                }
            }
            var from = 0
            while (from <= content.length - normalizedQuery.length) {
                val start = content.indexOf(normalizedQuery, from, ignoreCase = true)
                if (start < 0) break
                val end = start + normalizedQuery.length
                add(TextOccurrence(lineIndex, start, end, spans.filter {
                    it.start < end && it.endExclusive > start
                }.map { it.elementIndex }))
                from = end
            }
        }
    }
    return TextSearchResult(normalizedQuery, snapshot, occurrences)
}

private data class ElementSpan(val elementIndex: Int, val start: Int, val endExclusive: Int)

/** Collapse Kotlin whitespace without case folding/expansion or altering other characters. */
internal fun normalizeRecognizedText(text: String): String = buildString {
    var pendingSpace = false
    text.forEach { char ->
        if (char.isWhitespace()) {
            pendingSpace = isNotEmpty()
        } else {
            if (pendingSpace) append(' ')
            append(char)
            pendingSpace = false
        }
    }
}

private fun normalizeWhitespace(text: String): String = normalizeRecognizedText(text)
