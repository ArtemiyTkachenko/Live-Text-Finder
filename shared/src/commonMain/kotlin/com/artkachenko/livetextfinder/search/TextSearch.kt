package com.artkachenko.livetextfinder.search

import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.RecognizedTextContent
import com.artkachenko.livetextfinder.ocr.TextLine

/** Offsets refer to the normalized line, not character boxes or preview coordinates. */
internal data class TextOccurrence(
    val lineIndex: Int,
    val start: Int,
    val endExclusive: Int,
    val elementIndices: List<Int>,
    val termId: String = MANUAL_TERM_ID,
)

/** One occurrence and its normalized source line for still-image inspection. */
internal data class StillMatchRow(
    val index: Int,
    val occurrence: TextOccurrence,
    val matchedText: String,
    val lineContext: String,
)

/** A normalized OCR line assembled from the same element sequence used during matching. */
internal data class NormalizedElementLine(
    val text: String,
    val spans: List<ElementSpan>,
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
    val content: RecognizedTextContent?,
    val occurrences: List<TextOccurrence>,
    val spec: ActiveSearchSpec = manualSearchSpec(query),
) {
    val snapshot: OcrSnapshot? get() = content as? OcrSnapshot
}

/** Pure replacement result: inactive states deliberately discard any retained snapshot. */
internal fun searchRecognizedText(query: String, state: OcrUiState): TextSearchResult {
    return searchRecognizedText(manualSearchSpec(query), state)
}

internal fun searchRecognizedText(spec: ActiveSearchSpec, state: OcrUiState): TextSearchResult {
    val snapshot = state.snapshot.takeIf {
        state.status == OcrStatus.TextRecognized || state.status == OcrStatus.Scanning
    }
    return searchRecognizedContent(spec, snapshot)
}

internal fun searchRecognizedContent(
    spec: ActiveSearchSpec,
    content: RecognizedTextContent?,
): TextSearchResult {
    val normalizedTerms = spec.terms
        .map { term -> term.copy(text = normalizeWhitespace(term.text)) }
        .filter { term -> term.text.isNotEmpty() }
    val displayQuery = normalizedTerms.joinToString(" · ") { it.text }
    if (content == null || normalizedTerms.isEmpty()) {
        return TextSearchResult(displayQuery, content, emptyList(), spec)
    }
    val occurrences = buildList {
        content.lines.forEachIndexed { lineIndex, line ->
            val normalizedLine = normalizedElementLine(line)
            normalizedTerms.forEach { term ->
                var from = 0
                while (from <= normalizedLine.text.length - term.text.length) {
                    val start = normalizedLine.text.indexOf(term.text, from, ignoreCase = true)
                    if (start < 0) {
                        break
                    }
                    val end = start + term.text.length
                    add(TextOccurrence(lineIndex, start, end, normalizedLine.spans.filter {
                        it.start < end && it.endExclusive > start
                    }.map { it.elementIndex }, term.id))
                    from = end
                }
            }
        }
    }
    return TextSearchResult(displayQuery, content, occurrences, spec)
}

internal fun stillMatchRows(result: TextSearchResult): List<StillMatchRow> {
    val content = result.content ?: return emptyList()
    val normalizedLines = mutableMapOf<Int, NormalizedElementLine>()
    return result.occurrences.mapIndexedNotNull { index, occurrence ->
        val line = content.lines.getOrNull(occurrence.lineIndex) ?: return@mapIndexedNotNull null
        val normalized = normalizedLines.getOrPut(occurrence.lineIndex) {
            normalizedElementLine(line)
        }
        if (occurrence.start < 0 || occurrence.endExclusive > normalized.text.length ||
            occurrence.start >= occurrence.endExclusive
        ) {
            null
        } else {
            StillMatchRow(
                index = index,
                occurrence = occurrence,
                matchedText = normalized.text.substring(occurrence.start, occurrence.endExclusive),
                lineContext = normalized.text,
            )
        }
    }
}

internal fun stillMatchText(row: StillMatchRow, result: TextSearchResult): String? {
    if (result.content == null || row.index !in result.occurrences.indices ||
        result.occurrences[row.index] != row.occurrence
    ) {
        return null
    }
    val line = result.content.lines.getOrNull(row.occurrence.lineIndex) ?: return null
    val normalized = normalizedElementLine(line).text
    val occurrence = row.occurrence
    if (occurrence.start < 0 || occurrence.endExclusive > normalized.length ||
        occurrence.start >= occurrence.endExclusive
    ) {
        return null
    }
    val matchedText = normalized.substring(occurrence.start, occurrence.endExclusive)
    if (row.matchedText != matchedText) return null
    return matchedText
}

internal fun normalizedElementLine(line: TextLine): NormalizedElementLine {
    val text = StringBuilder()
    val spans = buildList {
        line.elements.forEachIndexed { index, element ->
            val elementText = normalizeWhitespace(element.text)
            if (elementText.isNotEmpty()) {
                if (text.isNotEmpty()) text.append(' ')
                val start = text.length
                text.append(elementText)
                add(ElementSpan(index, start, text.length))
            }
        }
    }
    return NormalizedElementLine(text.toString(), spans)
}

internal data class ElementSpan(val elementIndex: Int, val start: Int, val endExclusive: Int)

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
