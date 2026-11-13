package com.artkachenko.livetextfinder.search

internal const val MAX_LISTS = 50
internal const val MAX_MANUAL_QUERY_LENGTH = 10_000
internal const val MAX_NAME_LENGTH = 60
internal const val MAX_TERMS_PER_LIST = 32
internal const val MAX_TERM_LENGTH = 120
internal const val MAX_TOTAL_TERM_CHARS = 3_000

/** A presentation-only view of the exact normalized line parsing used when saving a list. */
internal data class SearchListTermsPreview(
    val terms: List<String>,
    val blankLineCount: Int,
)

internal fun previewSearchListTerms(input: String): SearchListTermsPreview {
    val lines = input.lines()
    val terms = lines.map(::normalizeRecognizedText)
    return SearchListTermsPreview(
        terms = terms.filter(String::isNotEmpty),
        blankLineCount = if (input.isEmpty()) 0 else terms.count(String::isEmpty),
    )
}

/** Validates and canonicalizes the terms entered for a saved search list. */
internal fun validateSearchListTerms(input: String): Result<List<String>> {
    val rawLines = input.lines()
    if (rawLines.size > MAX_TERMS_PER_LIST) {
        return Result.failure(SearchSessionException(SearchSessionError.TooManyTerms))
    }
    val terms = rawLines.map(::normalizeRecognizedText)
    if (terms.any { it.isEmpty() }) {
        return Result.failure(SearchSessionException(SearchSessionError.BlankTermLine))
    }
    if (terms.any { it.length > MAX_TERM_LENGTH }) {
        return Result.failure(SearchSessionException(SearchSessionError.TermTooLong))
    }
    if (terms.indices.any { index ->
            terms.take(index).any { it.equals(terms[index], ignoreCase = true) }
        }
    ) {
        return Result.failure(SearchSessionException(SearchSessionError.DuplicateTerms))
    }
    if (terms.sumOf { it.length } > MAX_TOTAL_TERM_CHARS) {
        return Result.failure(SearchSessionException(SearchSessionError.TooManyTermCharacters))
    }
    return Result.success(terms)
}

/** Validates the exact canonical shape accepted by the Android JSON store. */
internal fun validatePersistedSearchState(value: PersistedSearchState): SearchSessionError? {
    if (value.manualQuery.length > MAX_MANUAL_QUERY_LENGTH) {
        return SearchSessionError.ManualQueryTooLong
    }
    if (value.lists.size > MAX_LISTS) {
        return SearchSessionError.SavedTooManyLists
    }

    val seenIds = mutableSetOf<String>()
    val seenNames = mutableListOf<String>()
    for (list in value.lists) {
        if (list.id.length !in 1..100 || !seenIds.add(list.id)) {
            return SearchSessionError.SavedInvalidListId
        }
        if (list.name.isBlank() || list.name != list.name.trim() ||
            list.name.length > MAX_NAME_LENGTH
        ) {
            return SearchSessionError.SavedInvalidListName
        }
        if (seenNames.any { it.equals(list.name, ignoreCase = true) }) {
            return SearchSessionError.SavedDuplicateListNames
        }
        seenNames.add(list.name)
        if (list.terms.size !in 1..MAX_TERMS_PER_LIST) {
            return SearchSessionError.SavedInvalidTermCount
        }
        if (list.paletteSlots.size != list.terms.size ||
            list.paletteSlots.any { it !in SearchHighlightPalette.colors.indices } ||
            list.paletteSlots.toSet().size != list.paletteSlots.size
        ) {
            return SearchSessionError.SavedInvalidTermColors
        }

        var totalTermChars = 0
        val seenTerms = mutableListOf<String>()
        for (term in list.terms) {
            if (term.isBlank() || term != normalizeRecognizedText(term)) {
                return SearchSessionError.SavedNoncanonicalTerm
            }
            if (term.length > MAX_TERM_LENGTH) {
                return SearchSessionError.SavedTermTooLong
            }
            if (seenTerms.any { it.equals(term, ignoreCase = true) }) {
                return SearchSessionError.SavedDuplicateTerms
            }
            seenTerms.add(term)
            totalTermChars += term.length
        }
        if (totalTermChars > MAX_TOTAL_TERM_CHARS) {
            return SearchSessionError.SavedTooManyTermCharacters
        }
    }

    if (value.activeListId != null && seenIds.none { it == value.activeListId }) {
        return SearchSessionError.SavedSelectedListMissing
    }
    return null
}
