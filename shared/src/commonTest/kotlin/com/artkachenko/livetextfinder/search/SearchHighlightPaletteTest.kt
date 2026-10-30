package com.artkachenko.livetextfinder.search

import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SearchHighlightPaletteTest {
    @Test
    fun paletteHasThirtyTwoUniqueOpaqueColors() {
        assertEquals(32, SearchHighlightPalette.colors.size)
        assertEquals(32, SearchHighlightPalette.colors.map { it.argb }.toSet().size)
        assertEquals(setOf(0xFFL), SearchHighlightPalette.colors.map { it.argb ushr 24 }.toSet())
        assertTrue(SearchHighlightPalette.colors.all { relativeLuminance(it) >= 0.1 })
        val spec = ActiveSearchSpec(
            "maximum",
            "Maximum",
            (0 until 32).map { index -> SearchTerm("list:$index", "term $index") },
        )
        assertEquals(
            32,
            SearchHighlightPalette.colorsByTerm(spec, HighlightColor.Yellow).values
                .map { it.argb }
                .toSet()
                .size,
        )
    }

    @Test
    fun mappingSurvivesOrderCaseWhitespaceAndReconstructedSpecs() {
        val first = ActiveSearchSpec(
            "list",
            "List",
            listOf(SearchTerm("list:0", "tree"), SearchTerm("list:1", "nuts")),
        )
        val reconstructed = ActiveSearchSpec(
            "list",
            "List",
            listOf(SearchTerm("list:0", " NUTS "), SearchTerm("list:1", " TREE  ")),
        )

        assertEquals(
            colorsByLabel(first),
            colorsByLabel(reconstructed),
        )
    }

    @Test
    fun manualSearchUsesSelectedCustomColor() {
        val custom = HighlightColor.fromRgb(18, 171, 239)
        assertEquals(
            mapOf(MANUAL_TERM_ID to custom),
            SearchHighlightPalette.colorsByTerm(manualSearchSpec("tree nuts"), custom),
        )
    }

    @Test
    fun termSummaryCountsRepeatedPhraseOccurrencesAndIncludesZeroes() {
        val spec = ActiveSearchSpec(
            "phrases",
            "List",
            listOf(
                SearchTerm("phrase", "tree nuts"),
                SearchTerm("missing", "not here"),
            ),
        )
        val line = "tree nuts and tree nuts"
        val content = ImportedTextSnapshot(
            text = line,
            lines = listOf(
                TextLine(line, null, emptyList(), listOf(TextElement(line, null, emptyList()))),
            ),
            width = 100,
            height = 20,
            sourceIdentity = 1L,
        )
        val result = searchRecognizedContent(spec, content)
        val summaries = searchTermSummaries(result)

        assertEquals(listOf("tree nuts" to 2, "not here" to 0), summaries.map {
            it.label to it.occurrenceCount
        })
        assertNotEquals(summaries[0].color, summaries[1].color)
    }

    private fun colorsByLabel(spec: ActiveSearchSpec): Map<String, HighlightColor> {
        val colors = SearchHighlightPalette.colorsByTerm(spec, HighlightColor.Yellow)
        return spec.terms.associate { term ->
            normalizeRecognizedText(term.text).lowercase() to checkNotNull(colors[term.id])
        }
    }

    private fun relativeLuminance(color: HighlightColor): Double {
        fun channel(shift: Int): Double {
            val encoded = ((color.argb shr shift) and 0xFF).toDouble() / 255.0
            return if (encoded <= 0.04045) {
                encoded / 12.92
            } else {
                ((encoded + 0.055) / 1.055).pow(2.4)
            }
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
