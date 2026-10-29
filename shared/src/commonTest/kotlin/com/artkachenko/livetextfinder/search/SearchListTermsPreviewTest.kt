package com.artkachenko.livetextfinder.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchListTermsPreviewTest {
    @Test
    fun normalizesLinesWithoutSplittingCommasAndPreservesDuplicates() {
        assertEquals(
            SearchListTermsPreview(
                terms = listOf("red apple, ripe", "invoice", "invoice"),
                blankLineCount = 0,
            ),
            previewSearchListTerms("  red apple,   ripe  \r\n invoice \r\ninvoice"),
        )
    }

    @Test
    fun countsBlankLinesAndKeepsEmptyDraftEmpty() {
        assertEquals(
            SearchListTermsPreview(emptyList(), 0),
            previewSearchListTerms(""),
        )
        assertEquals(
            SearchListTermsPreview(listOf("term"), 3),
            previewSearchListTerms("\n term \n\n"),
        )
    }

    @Test
    fun preservesAllParsedTermsForLiveCountAndBoundedRendering() {
        val preview = previewSearchListTerms(
            (1..40).joinToString("\n") {
                "term $it"
            },
        )

        assertEquals(40, preview.terms.size)
        assertEquals(
            listOf("term 1", "term 2", "term 3", "term 4", "term 5"),
            preview.terms.take(5),
        )
    }
}
