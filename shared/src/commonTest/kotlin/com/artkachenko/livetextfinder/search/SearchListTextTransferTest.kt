package com.artkachenko.livetextfinder.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SearchListTextTransferTest {
    @Test
    fun parsesBomUnicodePhrasesAndLiteralCommas() {
        val result = parseSearchListText("\uFEFFCafé, noir\nTwo  words\tinside".encodeToByteArray())

        assertEquals(listOf("Café, noir", "Two words inside"), result.getOrThrow())
    }

    @Test
    fun parsesLfAndCrLfWithOptionalFinalNewlineAndPreservesOrder() {
        val lf = parseSearchListText("First\nSecond phrase".encodeToByteArray())
        val crlf = parseSearchListText("First\r\nSecond phrase\r\n".encodeToByteArray())

        assertEquals(listOf("First", "Second phrase"), lf.getOrThrow())
        assertEquals(lf.getOrThrow(), crlf.getOrThrow())
        assertEquals(
            "First\nSecond phrase\n",
            serializeSearchListText(crlf.getOrThrow()).decodeToString(),
        )
    }

    @Test
    fun rejectsInvalidUtf8AndOversizedFiles() {
        val invalidUtf8 = parseSearchListText(byteArrayOf(0xc3.toByte(), 0x28))
        val oversized = parseSearchListText(ByteArray(MAX_SEARCH_LIST_TRANSFER_BYTES + 1))
        val exactLimit = ("Term" + " ".repeat(MAX_SEARCH_LIST_TRANSFER_BYTES - 4))
            .encodeToByteArray()

        assertFailureContains(invalidUtf8, "valid UTF-8")
        assertFailureContains(oversized, "65,536 bytes")
        assertEquals(listOf("Term"), parseSearchListText(exactLimit).getOrThrow())
    }

    @Test
    fun rejectsEmptyInternalAndExtraTrailingBlankLines() {
        listOf("", "\n", "One\n\nTwo", "One\n\n").forEach { text ->
            assertFailureContains(parseSearchListText(text.encodeToByteArray()), "blank")
        }
    }

    @Test
    fun rejectsDuplicatesAfterWhitespaceCanonicalization() {
        val result = parseSearchListText("Match  phrase\nmatch phrase".encodeToByteArray())

        assertFailureContains(result, "unique")
    }

    @Test
    fun rejectsUnsupportedControlsAndBareCarriageReturns() {
        val nul = parseSearchListText("One\u0000term".encodeToByteArray())
        val del = parseSearchListText("One\u007fterm".encodeToByteArray())
        val bareCarriageReturn = parseSearchListText("One\rTwo".encodeToByteArray())

        assertFailureContains(nul, "control")
        assertFailureContains(del, "control")
        assertFailureContains(bareCarriageReturn, "LF or CRLF")
    }

    @Test
    fun acceptsExactTermAndCharacterLimits() {
        val terms = List(MAX_TERMS_PER_LIST - 1) { "Term $it" } + "x".repeat(MAX_TERM_LENGTH)

        assertEquals(
            terms,
            parseSearchListText(terms.joinToString("\n").encodeToByteArray()).getOrThrow(),
        )
        assertEquals(
            terms,
            serializeSearchListText(terms).decodeToString().removeSuffix("\n").split('\n'),
        )
        val totalLimitTerms = List(25) { index ->
            index.toString() + "x".repeat(MAX_TERM_LENGTH - index.toString().length)
        }
        assertEquals(MAX_TOTAL_TERM_CHARS, totalLimitTerms.sumOf(String::length))
        assertEquals(
            totalLimitTerms,
            parseSearchListText(totalLimitTerms.joinToString("\n").encodeToByteArray())
                .getOrThrow(),
        )
    }

    @Test
    fun rejectsTermCountLengthAndTotalCharacterOverflow() {
        val tooMany = List(MAX_TERMS_PER_LIST + 1) { "x" }.joinToString("\n")
        val tooLong = "x".repeat(MAX_TERM_LENGTH + 1)
        val totalOverflow = (List(25) { index ->
            index.toString() + "x".repeat(MAX_TERM_LENGTH - index.toString().length)
        } + "extra").joinToString("\n")

        assertFailureContains(parseSearchListText(tooMany.encodeToByteArray()), "at most 32")
        assertFailureContains(parseSearchListText(tooLong.encodeToByteArray()), "120 characters")
        assertFailureContains(
            parseSearchListText(totalOverflow.encodeToByteArray()),
            "total term characters",
        )
    }

    @Test
    fun serializationRejectsNoncanonicalTerms() {
        assertFailsWith<IllegalArgumentException> {
            serializeSearchListText(listOf("two  spaces"))
        }
        assertFailsWith<IllegalArgumentException> {
            serializeSearchListText(listOf("Term", "term"))
        }
        assertFailsWith<IllegalArgumentException> {
            serializeSearchListText(listOf("bad\u0000term"))
        }
    }

    @Test
    fun filenameHelpersStripExtensionsAndUnsafeCharacters() {
        assertEquals("My search", searchListImportName("My search.TXT", "Fallback"))
        assertEquals("My search.txt", searchListExportFileName("/My\\ search.txt"))
        assertEquals(
            "Localized fallback",
            searchListImportName("/\u0000.TXT", "Localized fallback"),
        )
        assertEquals("search-list.txt", searchListExportFileName("/\u0000"))
    }

    @Test
    fun filenameTruncationDoesNotSplitSurrogatePairs() {
        val name = "a".repeat(MAX_NAME_LENGTH - 1) + "😀tail"

        assertEquals("a".repeat(MAX_NAME_LENGTH - 1), searchListImportName(name, "Fallback"))
    }

    private fun assertFailureContains(result: Result<List<String>>, message: String) {
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains(message) == true)
    }
}
