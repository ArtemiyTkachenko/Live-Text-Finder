package com.artkachenko.livetextfinder.search

import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import com.artkachenko.livetextfinder.ocr.TextPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HighlightsTest {
    private val identity = listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private val frame = FrameInfo(1, 400, 200, 0, TextBounds(40, 20, 360, 180), identity)
    private val sensor = listOf(.5f, 0f, -50f, 0f, .5f, -25f, 0f, 0f, 1f)
    // Real fill-centre example: 320×160 crop within 400×200 buffer into 300×240 view.
    // Effective raw→view: (1.5x-150, 1.5y-30). No analysis crop-origin subtraction.
    private val landscape = PreviewTarget(300, 240, listOf(.75f, 0f, -225f, 0f, .75f, -67.5f, 0f, 0f, 1f))

    @Test fun asymmetricLandmarksRespectEveryFullBufferRotationAndNonidentitySensorMapping() {
        val upright = listOf(
            listOf(80f to 40f, 280f to 140f, 220f to 70f),
            listOf(160f to 80f, 60f to 280f, 130f to 220f),
            listOf(320f to 160f, 120f to 60f, 180f to 130f),
            listOf(40f to 320f, 140f to 120f, 70f to 180f),
        )
        val expected = listOf(-30f to 30f, 270f to 180f, 180f to 75f)
        listOf(0, 90, 180, 270).forEachIndexed { index, rotation ->
            val transform = assertNotNull(uprightToPreview(frame.copy(rotationDegrees = rotation, sensorToBufferMatrix = sensor), landscape))
            upright[index].zip(expected).forEach { (input, output) -> assertPoint(output, transform.map(PreviewPoint(input.first, input.second))) }
        }
    }

    @Test fun portraitCropRotationAndTranslationHaveIndependentExpectedLandmarks() {
        // 90° clockwise display of the same crop into 120×300; uniform fill scale .9375.
        val target = PreviewTarget(120, 300, listOf(0f, -.46875f, 177.1875f, .46875f, 0f, -84.375f, 0f, 0f, 1f))
        val transform = assertNotNull(uprightToPreview(frame.copy(sensorToBufferMatrix = sensor), target))
        assertPoint(116.25f to 37.5f, transform.map(PreviewPoint(80f, 40f)))
        assertPoint(22.5f to 225f, transform.map(PreviewPoint(280f, 140f)))
        assertPoint(88.125f to 168.75f, transform.map(PreviewPoint(220f, 70f)))
    }

    @Test fun localPixelCoordinatesAreNotDpScaledOrShiftedByScreenInsets() {
        val target = PreviewTarget(400, 200, listOf(1f, 0f, 17f, 0f, 1f, 9f, 0f, 0f, 1f))
        val transform = assertNotNull(uprightToPreview(frame, target))
        assertPoint(37f to 39f, transform.map(PreviewPoint(20f, 30f)))
        // Overlay must have exact native pixel size, independent of external screen/density.
        val snapshot = snapshot(element())
        assertFalse(draw(snapshot, "foo", target, width = 200, height = 100).ready)
        assertTrue(draw(snapshot, "foo", target).ready)
    }

    @Test fun invalidFrameDimensionsRotationsAndCropReturnNoTransform() {
        listOf(frame.copy(bufferWidth = 0), frame.copy(bufferHeight = -1), frame.copy(rotationDegrees = 45),
            frame.copy(crop = TextBounds(-1, 0, 100, 100)), frame.copy(crop = TextBounds(0, -1, 100, 100)),
            frame.copy(crop = TextBounds(0, 0, 401, 100)), frame.copy(crop = TextBounds(0, 0, 100, 201)),
            frame.copy(crop = TextBounds(10, 10, 10, 20)), frame.copy(crop = TextBounds(0, 20, 10, 10)),
        ).forEach { assertNull(uprightToPreview(it, PreviewTarget(400, 200, identity))) }
    }

    @Test fun missingMalformedNonfiniteAndSingularMatricesSuppressMapping() {
        val bad = listOf(emptyList(), listOf(1f), List(9) { 0f }, identity.mapIndexed { i, v -> if (i == 3) Float.NaN else v },
            identity.mapIndexed { i, v -> if (i == 2) Float.POSITIVE_INFINITY else v })
        bad.forEach { values ->
            assertNull(uprightToPreview(frame.copy(sensorToBufferMatrix = values), PreviewTarget(400, 200, identity)))
            assertNull(uprightToPreview(frame, PreviewTarget(400, 200, values)))
        }
        assertNull(uprightToPreview(frame, PreviewTarget(0, 200, identity)))
        assertNull(uprightToPreview(frame, PreviewTarget(400, -1, identity)))
        val snapshot = snapshot(element())
        val search = searchRecognizedText("foo", OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot))
        assertFalse(projectHighlights(search, null, 400, 200).ready)
    }

    @Test fun projectedOverflowOrVanishingHomogeneousDenominatorIsRejected() {
        val overflow = PreviewTarget(400, 200, listOf(Float.MAX_VALUE, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))
        assertTrue(draw(snapshot(element()), "foo", overflow).outlines.isEmpty())
        // CameraX supplies affine transforms; unexpected perspective is suppressed, not guessed.
        assertNull(PixelTransform.from(listOf(1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, -10f)))
        val matrix = PixelTransform.from(listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f))!!
        assertNull(matrix.map(PreviewPoint(10f, 20f)))
    }

    @Test fun validOrderedCornersTakePriorityOverBounds() {
        val corners = listOf(TextPoint(10, 20), TextPoint(60, 10), TextPoint(70, 40), TextPoint(20, 50))
        val result = draw(snapshot(element(bounds = TextBounds(0, 0, 2, 2), corners = corners)), "foo")
        assertEquals(corners.map { PreviewPoint(it.x.toFloat(), it.y.toFloat()) }, result.outlines.single().points)
    }

    @Test fun degenerateOrCrossedCornersFallBackToValidBounds() {
        val badCorners = listOf(emptyList(), listOf(TextPoint(1, 1)), List(4) { TextPoint(1, 1) },
            listOf(TextPoint(0, 0), TextPoint(10, 10), TextPoint(0, 10), TextPoint(10, 0)))
        badCorners.forEach { corners ->
            assertEquals(listOf(PreviewPoint(20f, 30f), PreviewPoint(80f, 30f), PreviewPoint(80f, 60f), PreviewPoint(20f, 60f)),
                draw(snapshot(element(corners = corners)), "foo").outlines.single().points)
        }
    }

    @Test fun missingAndDegenerateGeometryNeverDiscardsTextCounts() {
        val snapshot = snapshot(element(bounds = null), element(bounds = TextBounds(10, 10, 10, 20)))
        val search = searchRecognizedText("foo", OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot))
        assertEquals(2, search.occurrences.size)
        val outlines = projectHighlights(search, FrameProjection(snapshot, FrameIdentity(1, 1), PreviewTarget(400, 200, identity)), 400, 200)
        assertTrue(outlines.ready)
        assertTrue(outlines.outlines.isEmpty())
        assertEquals(2, search.occurrences.size)
    }

    @Test fun fullyOutsideIsRejectedAndPartlyVisiblePolygonRetainsUnclampedVertices() {
        assertTrue(draw(snapshot(element(bounds = TextBounds(500, 20, 600, 40))), "foo").outlines.isEmpty())
        val partial = draw(snapshot(element(bounds = TextBounds(-20, 20, 40, 60))), "foo").outlines.single()
        assertEquals(PreviewPoint(-20f, 20f), partial.points.first())
        assertEquals(PreviewPoint(40f, 60f), partial.points[2])
        // No vertex is inside, but this polygon surrounds the preview and must intersect it.
        assertEquals(1, draw(snapshot(element(bounds = TextBounds(-100, -100, 500, 300))), "foo").outlines.size)
        assertTrue(draw(snapshot(element(bounds = TextBounds(400, 0, 420, 30))), "foo").outlines.isEmpty())
    }

    @Test fun repeatedOccurrencesDeduplicateAndPhrasesSelectOriginalElementIndices() {
        val snapshot = snapshot(element("foofoo"), element(" "), element("bar"))
        val count = searchRecognizedText("foo", OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot)).occurrences.size
        assertEquals(2, count)
        assertEquals(listOf(0), draw(snapshot, "foo").outlines.map { it.elementIndex })
        assertEquals(listOf(0, 2), draw(snapshot, "foo bar").outlines.map { it.elementIndex })
    }

    @Test
    fun overlappingTermsCountSeparatelyButOutlineSharedElementOnce() {
        val snapshot = snapshot(element("foobar"))
        val spec = ActiveSearchSpec(
            "parts",
            "Parts",
            listOf(SearchTerm("foo", "foo"), SearchTerm("foobar", "foobar")),
        )
        val search = searchRecognizedText(
            spec,
            OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot),
        )
        val projection = FrameProjection(
            snapshot,
            FrameIdentity(1, 1),
            PreviewTarget(400, 200, identity),
        )

        val highlights = projectHighlights(search, projection, 400, 200)

        assertEquals(2, search.occurrences.size)
        assertEquals(listOf(0), highlights.outlines.map { it.elementIndex })
        assertEquals(listOf("foo", "foobar"), highlights.outlines.single().termIds)
    }

    @Test
    fun containedTermsKeepOwnersAcrossLiveFrozenAndImportedProjections() {
        val cameraSnapshot = snapshot(element("nuts"))
        val spec = ActiveSearchSpec(
            "contained",
            "Contained",
            listOf(SearchTerm("nut", "nut"), SearchTerm("nuts", "nuts")),
        )
        val cameraSearch = searchRecognizedText(
            spec,
            OcrUiState(OcrStatus.TextRecognized, snapshot = cameraSnapshot),
        )
        val liveProjection = FrameProjection(
            cameraSnapshot,
            FrameIdentity(1, 1),
            PreviewTarget(400, 200, identity),
        )
        val live = projectHighlights(cameraSearch, liveProjection, 400, 200)
        val frozen = projectFrozenHighlights(cameraSearch, 400f, 200f)
        val still = ImportedTextSnapshot(
            cameraSnapshot.text,
            cameraSnapshot.lines,
            400,
            200,
            9L,
        )
        val importedSearch = searchRecognizedContent(spec, still)
        val imported = projectImportedHighlights(importedSearch, still, 400f, 200f)

        assertEquals(2, cameraSearch.occurrences.size)
        assertEquals(listOf(0), live.outlines.map { it.elementIndex })
        assertEquals(listOf("nut", "nuts"), live.outlines.single().termIds)
        assertEquals(live.outlines.single().termIds, frozen.outlines.single().termIds)
        assertEquals(live.outlines.single().termIds, imported.outlines.single().termIds)
        assertEquals(live.outlines.single().points, frozen.outlines.single().points)
        assertEquals(live.outlines.single().points, imported.outlines.single().points)
    }

    @Test fun equalTextOnSeparateLinesRetainsSeparateOutlineIdentity() {
        val first = snapshot(element())
        val snapshot = first.copy(lines = first.lines + first.lines)
        assertEquals(listOf(0, 1), draw(snapshot, "foo").outlines.map { it.lineIndex })
    }

    @Test fun queryEditsClearAndNewEmptySnapshotReplaceOutlinesWithoutStaleSourceReuse() {
        val snapshot = snapshot(element("foo"), element("bar"))
        assertEquals(listOf(0), draw(snapshot, "foo").outlines.map { it.elementIndex })
        assertEquals(listOf(1), draw(snapshot, "bar").outlines.map { it.elementIndex })
        assertTrue(draw(snapshot, "").outlines.isEmpty())
        val empty = snapshot.copy(lines = emptyList(), frame = frame.copy(timestampNanos = 2))
        assertTrue(draw(empty, "foo").outlines.isEmpty())
        val oldProjection = FrameProjection(snapshot, FrameIdentity(1, 1), PreviewTarget(400, 200, identity))
        val replaced = searchRecognizedText("foo", OcrUiState(OcrStatus.Scanning, snapshot = empty))
        assertFalse(projectHighlights(replaced, oldProjection, 400, 200).ready)
    }

    private fun assertPoint(expected: Pair<Float, Float>, actual: PreviewPoint?) {
        assertNotNull(actual)
        assertEquals(expected.first, actual.x, .001f)
        assertEquals(expected.second, actual.y, .001f)
    }
    private fun element(text: String = "foo", bounds: TextBounds? = TextBounds(20, 30, 80, 60), corners: List<TextPoint> = emptyList()) =
        TextElement(text, bounds, corners)
    private fun snapshot(vararg elements: TextElement) = OcrSnapshot("diagnostic", listOf(TextLine("diagnostic", null, emptyList(), elements.toList())), frame)
    private fun draw(snapshot: OcrSnapshot, query: String, target: PreviewTarget = PreviewTarget(400, 200, identity), width: Int = target.width, height: Int = target.height): Highlights {
        val search = searchRecognizedText(query, OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot))
        return projectHighlights(search, FrameProjection(snapshot, FrameIdentity(1, 1), target), width, height)
    }
}
