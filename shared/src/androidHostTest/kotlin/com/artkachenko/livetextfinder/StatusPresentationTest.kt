package com.artkachenko.livetextfinder

import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusPresentationTest {
    private val recognized = ImportedTextSnapshot(
        text = "invoice paid",
        lines = listOf(
            TextLine(
                text = "invoice paid",
                bounds = null,
                corners = emptyList(),
                elements = listOf(
                    TextElement("invoice", null, emptyList()),
                    TextElement("paid", null, emptyList()),
                ),
            ),
        ),
        width = 640,
        height = 480,
        sourceIdentity = 1,
    )

    @Test
    fun importedAndFrozenSourcesWinOverConcurrentLiveCameraState() {
        assertEquals(
            StatusSource.ImportProgress,
            resolveStatusSource(ImportedImagePhase.Recognizing, FreezeMode.Frozen),
        )
        assertEquals(
            StatusSource.ImportedImage,
            resolveStatusSource(ImportedImagePhase.Imported, FreezeMode.Live),
        )
        assertEquals(
            StatusSource.FrozenFrame,
            resolveStatusSource(ImportedImagePhase.Error, FreezeMode.Frozen),
        )
        assertEquals(
            StatusSource.LiveCamera,
            resolveStatusSource(ImportedImagePhase.Error, FreezeMode.Live),
        )
        assertEquals(
            StatusSource.FreezingFrame,
            resolveStatusSource(ImportedImagePhase.Live, FreezeMode.Freezing),
        )
    }

    @Test
    fun queryAndLiveSnapshotPrecedenceDoesNotReportFalseNoMatches() {
        assertEquals(
            SearchFeedbackKind.EnterQuery,
            searchFeedback("", null, 0, hasLiveSnapshot = false).kind,
        )
        assertEquals(
            SearchFeedbackKind.WaitingForFreshText,
            searchFeedback("invoice", null, 0, hasLiveSnapshot = false).kind,
        )
        assertEquals(
            SearchFeedbackKind.WaitingForContent,
            searchFeedback("invoice", null, 0).kind,
        )
        assertEquals(
            SearchFeedbackKind.NoReadableText,
            searchFeedback("invoice", recognized.copy(text = "", lines = emptyList()), 0).kind,
        )
        val retainedDuringDropout = searchFeedback(
            "invoice",
            recognized.copy(text = "", lines = emptyList()),
            matchCount = 0,
            retainedOutlines = true,
        )
        assertEquals(SearchFeedbackKind.NoReadableText, retainedDuringDropout.kind)
        assertTrue(retainedDuringDropout.retainedOutlines)
    }

    @Test
    fun areaReadinessAndNoMatchStatesHaveExplicitPrecedence() {
        assertEquals(
            SearchFeedbackKind.WaitingForAreaMapping,
            searchFeedback(
                "invoice",
                recognized,
                matchCount = 0,
                areaSelected = true,
                areaReady = false,
            ).kind,
        )
        assertEquals(
            SearchFeedbackKind.NoMatches,
            searchFeedback("invoice", recognized, matchCount = 0).kind,
        )
        assertEquals(
            SearchFeedbackKind.NoMatchesInArea,
            searchFeedback(
                "invoice",
                recognized,
                matchCount = 0,
                areaSelected = true,
            ).kind,
        )
    }

    @Test
    fun matchesRetainedOutlinesAndUnavailableHighlightsRemainDistinct() {
        val singular = searchFeedback("invoice", recognized, matchCount = 1)
        assertEquals(SearchFeedbackKind.Matches, singular.kind)
        assertEquals(1, singular.matchCount)

        val plural = searchFeedback("invoice", recognized, matchCount = 3)
        assertEquals(SearchFeedbackKind.Matches, plural.kind)
        assertEquals(3, plural.matchCount)

        val waiting = searchFeedback(
            "invoice",
            recognized,
            matchCount = 2,
            highlightsReady = false,
        )
        assertEquals(SearchFeedbackKind.WaitingForHighlights, waiting.kind)

        val retainedOnly = searchFeedback(
            "invoice",
            recognized,
            matchCount = 0,
            retainedOutlines = true,
        )
        assertEquals(SearchFeedbackKind.NoMatches, retainedOnly.kind)
        assertTrue(retainedOnly.retainedOutlines)

        val retainedAlongsideCurrent = searchFeedback(
            "invoice",
            recognized,
            matchCount = 1,
            retainedOutlines = true,
        )
        assertTrue(retainedAlongsideCurrent.retainedOutlines)
        assertFalse(retainedOnly.matchCount > 0)
    }

    @Test
    fun detailsUseOnlyTheSelectedSourceAndBoundTheTextSample() {
        val imported = recognitionDetails(
            source = StatusSource.ImportedImage,
            content = recognized,
            outlinedElementCount = 2,
            retainedOutlines = false,
        )
        assertEquals(StatusSource.ImportedImage, imported.source)
        assertEquals(1, imported.recognizedLineCount)
        assertEquals(640, imported.imageWidth)
        assertEquals(480, imported.imageHeight)
        assertEquals(null, imported.rotationDegrees)
        assertEquals("invoice paid", imported.textSample)
        assertEquals(2, imported.outlinedElementCount)

        val unavailableLive = recognitionDetails(
            source = StatusSource.LiveCamera,
            content = null,
            outlinedElementCount = null,
            retainedOutlines = true,
            sourceMessage = "Camera access is disabled.",
        )
        assertEquals(null, unavailableLive.recognizedLineCount)
        assertEquals(null, unavailableLive.imageWidth)
        assertEquals(null, unavailableLive.outlinedElementCount)
        assertTrue(unavailableLive.textUnavailable)
        assertTrue(unavailableLive.retainedOutlines)
        assertEquals("Camera access is disabled.", unavailableLive.sourceMessage)

        val longSample = "word ".repeat(100)
        val bounded = recognitionDetails(
            StatusSource.ImportedImage,
            recognized.copy(text = longSample),
            outlinedElementCount = 0,
            retainedOutlines = false,
        )
        assertEquals(240, bounded.textSample?.length)
    }
}
