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
    val termId: String = MANUAL_TERM_ID,
)

internal const val MANUAL_TERM_ID = "manual"

/** Immutable identity for the complete active search, including mode and every term. */
data class ActiveSearchSpec(
    val identity: String,
    val displayName: String,
    val terms: List<SearchTerm>,
)

/** A stable identity paired with one independent literal term or phrase. */
data class SearchTerm(val id: String, val text: String)

internal fun manualSearchSpec(query: String): ActiveSearchSpec = ActiveSearchSpec(
    identity = "manual\u0000$query",
    displayName = "Manual search",
    terms = if (query.isEmpty()) emptyList() else listOf(SearchTerm(MANUAL_TERM_ID, query)),
)

/** Original snapshot retains frame metadata and geometry for every referenced element. */
internal data class TextSearchResult(
    val query: String,
    val snapshot: OcrSnapshot?,
    val occurrences: List<TextOccurrence>,
    val spec: ActiveSearchSpec = manualSearchSpec(query),
)

/** Pure replacement result: inactive states deliberately discard any retained snapshot. */
internal fun searchRecognizedText(query: String, state: OcrUiState): TextSearchResult {
    return searchRecognizedText(manualSearchSpec(query), state)
}

internal fun searchRecognizedText(spec: ActiveSearchSpec, state: OcrUiState): TextSearchResult {
    val normalizedTerms = spec.terms
        .map { term -> term.copy(text = normalizeWhitespace(term.text)) }
        .filter { term -> term.text.isNotEmpty() }
    val snapshot = state.snapshot.takeIf {
        state.status == OcrStatus.TextRecognized || state.status == OcrStatus.Scanning
    }
    val displayQuery = normalizedTerms.joinToString(" · ") { it.text }
    if (snapshot == null || normalizedTerms.isEmpty()) {
        return TextSearchResult(displayQuery, snapshot, emptyList(), spec)
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
            normalizedTerms.forEach { term ->
                var from = 0
                while (from <= content.length - term.text.length) {
                    val start = content.indexOf(term.text, from, ignoreCase = true)
                    if (start < 0) {
                        break
                    }
                    val end = start + term.text.length
                    add(TextOccurrence(lineIndex, start, end, spans.filter {
                        it.start < end && it.endExclusive > start
                    }.map { it.elementIndex }, term.id))
                    from = end
                }
            }
        }
    }
    return TextSearchResult(displayQuery, snapshot, occurrences, spec)
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
