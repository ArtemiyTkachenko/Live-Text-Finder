package com.artkachenko.livetextfinder.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchSessionErrorTest {
    @Test
    fun typedFailure_keepsItsErrorAndOtherFailuresUseTheFallback() {
        val typed = SearchSessionException(SearchSessionError.SavedDuplicateTerms)

        assertEquals(
            SearchSessionError.SavedDuplicateTerms,
            typed.toSearchSessionError(SearchSessionError.StorageUnreadable),
        )
        assertEquals(
            SearchSessionError.StorageUnreadable,
            IllegalStateException("disk full")
                .toSearchSessionError(SearchSessionError.StorageUnreadable),
        )
        assertEquals(
            SearchSessionError.StorageSaveFailed,
            null.toSearchSessionError(SearchSessionError.StorageSaveFailed),
        )
    }

    @Test
    fun termValidation_reportsTypedErrors() {
        fun errorOf(input: String) =
            (validateSearchListTerms(input).exceptionOrNull() as? SearchSessionException)?.error

        assertEquals(SearchSessionError.BlankTermLine, errorOf("apple\n "))
        assertEquals(SearchSessionError.DuplicateTerms, errorOf("apple\nAPPLE"))
        assertEquals(SearchSessionError.TermTooLong, errorOf("x".repeat(MAX_TERM_LENGTH + 1)))
        assertEquals(
            SearchSessionError.TooManyTerms,
            errorOf(List(MAX_TERMS_PER_LIST + 1) { "t$it" }.joinToString("\n")),
        )
    }
}
