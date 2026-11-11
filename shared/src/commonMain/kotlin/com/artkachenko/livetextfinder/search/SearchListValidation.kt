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
        return Result.failure(
            IllegalArgumentException("Lists can contain at most $MAX_TERMS_PER_LIST terms."),
        )
    }
    val terms = rawLines.map(::normalizeRecognizedText)
    if (terms.any { it.isEmpty() }) {
        return Result.failure(
            IllegalArgumentException(
                "Remove blank term lines. Each line must contain a term or phrase.",
            ),
        )
    }
    if (terms.any { it.length > MAX_TERM_LENGTH }) {
        return Result.failure(
            IllegalArgumentException("Each term can contain at most $MAX_TERM_LENGTH characters."),
        )
    }
    if (terms.indices.any { index ->
            terms.take(index).any { it.equals(terms[index], ignoreCase = true) }
        }
    ) {
        return Result.failure(IllegalArgumentException("Terms must be unique, ignoring case."))
    }
    if (terms.sumOf { it.length } > MAX_TOTAL_TERM_CHARS) {
        return Result.failure(
            IllegalArgumentException(
                "A list can contain at most $MAX_TOTAL_TERM_CHARS total term characters.",
            ),
        )
    }
    return Result.success(terms)
}

/** Validates the exact canonical shape accepted by the Android JSON store. */
internal fun validatePersistedSearchState(value: PersistedSearchState): String? {
    if (value.manualQuery.length > MAX_MANUAL_QUERY_LENGTH) {
        return "Manual queries can contain at most $MAX_MANUAL_QUERY_LENGTH characters."
    }
    if (value.lists.size > MAX_LISTS) {
        return "Saved search data contains too many lists."
    }

    val seenIds = mutableSetOf<String>()
    val seenNames = mutableListOf<String>()
    for (list in value.lists) {
        if (list.id.length !in 1..100 || !seenIds.add(list.id)) {
            return "Saved search data contains an invalid or duplicate list ID."
        }
        if (list.name.isBlank() || list.name != list.name.trim() ||
            list.name.length > MAX_NAME_LENGTH
        ) {
            return "Saved search data contains an invalid list name."
        }
        if (seenNames.any { it.equals(list.name, ignoreCase = true) }) {
            return "Saved search data contains duplicate list names."
        }
        seenNames.add(list.name)
        if (list.terms.size !in 1..MAX_TERMS_PER_LIST) {
            return "Saved search data contains an invalid term count."
        }
        if (list.paletteSlots.size != list.terms.size ||
            list.paletteSlots.any { it !in SearchHighlightPalette.colors.indices } ||
            list.paletteSlots.toSet().size != list.paletteSlots.size
        ) {
            return "Saved search data contains invalid term colors."
        }

        var totalTermChars = 0
        val seenTerms = mutableListOf<String>()
        for (term in list.terms) {
            if (term.isBlank() || term != normalizeRecognizedText(term)) {
                return "Saved search data contains a blank or noncanonical term."
            }
            if (term.length > MAX_TERM_LENGTH) {
                return "Saved search data contains a term that is too long."
            }
            if (seenTerms.any { it.equals(term, ignoreCase = true) }) {
                return "Saved search data contains duplicate terms."
            }
            seenTerms.add(term)
            totalTermChars += term.length
        }
        if (totalTermChars > MAX_TOTAL_TERM_CHARS) {
            return "Saved search data contains too many term characters."
        }
    }

    if (value.activeListId != null && seenIds.none { it == value.activeListId }) {
        return "The selected saved search list is missing."
    }
    return null
}
