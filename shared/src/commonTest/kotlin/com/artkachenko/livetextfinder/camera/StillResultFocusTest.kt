package com.artkachenko.livetextfinder.camera

import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import com.artkachenko.livetextfinder.ocr.TextPoint
import com.artkachenko.livetextfinder.search.ElementOutline
import com.artkachenko.livetextfinder.search.Highlights
import com.artkachenko.livetextfinder.search.PreviewPoint
import com.artkachenko.livetextfinder.search.TextOccurrence
import com.artkachenko.livetextfinder.search.manualSearchSpec
import com.artkachenko.livetextfinder.search.occurrenceBounds
import com.artkachenko.livetextfinder.search.projectFrozenHighlights
import com.artkachenko.livetextfinder.search.projectImportedHighlights
import com.artkachenko.livetextfinder.search.searchRecognizedContent
import com.artkachenko.livetextfinder.search.searchRecognizedText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StillResultFocusTest {
    @Test
    fun occurrenceBoundsUnionsOnlyReferencedLineAndElementIds() {
        val highlights = Highlights(
            ready = true,
            outlines = listOf(
                rectangle(8, 0, 40f, 40f, 50f, 50f),
                rectangle(2, 1, 100f, 80f, 120f, 100f),
                rectangle(1, 0, 10f, 20f, 20f, 30f),
                rectangle(3, 0, -20f, -10f, 0f, 0f),
            ),
        )

        val bounds = occurrenceBounds(
            TextOccurrence(0, 0, 4, listOf(3, 1, 3)),
            highlights,
        )

        assertEquals(OutlineBoundsExpected(-20f, -10f, 20f, 30f), bounds?.toExpected())
    }

    @Test
    fun repeatedAndOverlappingOccurrencesKeepTheirOwnPolygons() {
        val highlights = Highlights(
            ready = true,
            outlines = listOf(
                rectangle(2, 0, 30f, 40f, 42f, 52f),
                rectangle(1, 0, 8f, 12f, 18f, 22f),
            ),
        )
        val first = TextOccurrence(0, 0, 3, listOf(1), "short")
        val second = TextOccurrence(0, 1, 5, listOf(2), "long")
        val repeated = TextOccurrence(0, 8, 11, listOf(1), "short")

        assertEquals(OutlineBoundsExpected(8f, 12f, 18f, 22f),
            occurrenceBounds(first, highlights)?.toExpected())
        assertEquals(OutlineBoundsExpected(30f, 40f, 42f, 52f),
            occurrenceBounds(second, highlights)?.toExpected())
        assertEquals(occurrenceBounds(first, highlights), occurrenceBounds(repeated, highlights))
    }

    @Test
    fun occurrenceBoundsRejectsMissingUnreadyAndInvalidGeometry() {
        val occurrence = TextOccurrence(0, 0, 3, listOf(0))
        assertNull(occurrenceBounds(occurrence, Highlights(false, emptyList())))
        assertNull(occurrenceBounds(occurrence, Highlights(true, emptyList())))
        assertNull(
            occurrenceBounds(
                occurrence,
                Highlights(true, listOf(ElementOutline(0, 0, listOf(
                    PreviewPoint(0f, 0f),
                    PreviewPoint(Float.NaN, 0f),
                    PreviewPoint(10f, 10f),
                    PreviewPoint(0f, 10f),
                )))),
            ),
        )
        assertNull(
            occurrenceBounds(
                occurrence,
                Highlights(true, listOf(ElementOutline(0, 0, listOf(
                    PreviewPoint(0f, 0f),
                    PreviewPoint(10f, 10f),
                    PreviewPoint(0f, 10f),
                    PreviewPoint(10f, 0f),
                )))),
            ),
        )

        val invalid = searchRecognizedText("foo", recognized(invalidGeometrySnapshot()))
        assertNull(occurrenceBounds(
            invalid.occurrences.single(),
            projectFrozenHighlights(invalid, 20f, 20f),
        ))
    }

    @Test
    fun focusCentersTargetWithKnownZoomAndPan() {
        val fit = FrozenFrameFit(200, 100, 0, 200f, 100f)

        val focused = StillViewTransform().focus(40f, 20f, 60f, 40f, 200f, 100f, fit, 10f)

        assertEquals(4f, focused?.zoom)
        assertEquals(200f, focused?.panX)
        assertEquals(80f, focused?.panY)
        val topLeft = focused?.map(PreviewPoint(40f, 20f), 200f, 100f)
        assertEquals(PreviewPoint(60f, 10f), topLeft)
        assertEquals(PreviewPoint(140f, 90f), focused?.map(PreviewPoint(60f, 40f), 200f, 100f))
    }

    @Test
    fun focusClampsZoomAndKeepsOversizedPhraseAtOneTimes() {
        val fit = FrozenFrameFit(200, 100, 0, 200f, 100f)
        val tiny = StillViewTransform().focus(99f, 49f, 101f, 51f, 200f, 100f, fit, 0f)
        val large = StillViewTransform().focus(20f, 10f, 180f, 90f, 200f, 100f, fit, 10f)

        assertEquals(4f, tiny?.zoom)
        assertEquals(1f, large?.zoom)
        assertEquals(0f, large?.panX)
        assertEquals(0f, large?.panY)
    }

    @Test
    fun focusRespectsImagePanBoundsAndLetterbox() {
        val fit = FrozenFrameFit(200, 100, 0, 100f, 100f)

        val focused = StillViewTransform().focus(0f, 45f, 10f, 55f, 100f, 100f, fit, 0f)

        assertEquals(4f, focused?.zoom)
        assertEquals(150f, focused?.panX)
        assertEquals(0f, focused?.panY)
        assertEquals(PreviewPoint(0f, 30f), focused?.map(PreviewPoint(0f, 45f), 100f, 100f))
    }

    @Test
    fun frozenFocusUsesAlreadyUprightProjectionAtEveryQuarterTurn() {
        val expectedCenters = mapOf(
            0 to PreviewPoint(5f, 8.75f),
            90 to PreviewPoint(11.25f, 5f),
            180 to PreviewPoint(15f, 11.25f),
            270 to PreviewPoint(8.75f, 15f),
        )
        expectedCenters.forEach { (rotation, sourceCenter) ->
            val search = searchRecognizedText("foo", recognized(frozenSnapshot(rotation)))
            val highlights = projectFrozenHighlights(search, 20f, 20f)
            val bounds = occurrenceBounds(search.occurrences.single(), highlights)!!
            val fit = FrozenFrameFit(8, 4, rotation, 20f, 20f)

            val focused = StillViewTransform().focus(
                bounds.left, bounds.top, bounds.right, bounds.bottom,
                20f, 20f, fit, 2f,
            )

            assertEquals(PreviewPoint(10f, 10f), focused?.map(sourceCenter, 20f, 20f))
        }
    }

    @Test
    fun importedProjectionAndResizeDriveFocusFromCurrentViewportGeometry() {
        val snapshot = importedSnapshot()
        val search = searchRecognizedContent(manualSearchSpec("target"), snapshot)
        val initial = projectImportedHighlights(search, snapshot, 20f, 20f)
        val resized = projectImportedHighlights(search, snapshot, 40f, 20f)
        val initialBounds = occurrenceBounds(search.occurrences.single(), initial)!!
        val resizedBounds = occurrenceBounds(search.occurrences.single(), resized)!!
        val resizedFit = FrozenFrameFit(8, 4, 0, 40f, 20f)

        val fromOldTransform = StillViewTransform(2f, 15f, -3f).focus(
            resizedBounds.left, resizedBounds.top, resizedBounds.right, resizedBounds.bottom,
            40f, 20f, resizedFit, 2f,
        )
        val fresh = StillViewTransform().focus(
            resizedBounds.left, resizedBounds.top, resizedBounds.right, resizedBounds.bottom,
            40f, 20f, resizedFit, 2f,
        )

        assertEquals(OutlineBoundsExpected(2.5f, 7.5f, 7.5f, 10f), initialBounds.toExpected())
        assertEquals(OutlineBoundsExpected(5f, 5f, 15f, 10f), resizedBounds.toExpected())
        assertEquals(fresh, fromOldTransform)
        assertEquals(PreviewPoint(20f, 10f), fresh?.map(PreviewPoint(10f, 7.5f), 40f, 20f))
    }

    @Test
    fun invalidViewportOrFocusGeometryReturnsNull() {
        val fit = FrozenFrameFit(200, 100, 0, 100f, 50f)
        assertNull(StillViewTransform().focus(1f, 1f, 2f, 2f, 0f, 50f, fit, 0f))
        assertNull(StillViewTransform().focus(1f, 1f, 2f, 2f, 100f, Float.NaN, fit, 0f))
        assertNull(StillViewTransform().focus(Float.NaN, 1f, 2f, 2f, 100f, 50f, fit, 0f))
        assertNull(StillViewTransform().focus(2f, 1f, 2f, 2f, 100f, 50f, fit, 0f))
        assertNull(StillViewTransform().focus(1f, 1f, 2f, 2f, 100f, 50f, fit, -1f))
        assertNull(StillViewTransform().focus(1f, 1f, 2f, 2f, 100f, 50f,
            FrozenFrameFit(0, 100, 0, 100f, 50f), 0f))
    }

    private fun rectangle(
        elementIndex: Int,
        lineIndex: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) = ElementOutline(
        lineIndex = lineIndex,
        elementIndex = elementIndex,
        points = listOf(
            PreviewPoint(left, top),
            PreviewPoint(right, top),
            PreviewPoint(right, bottom),
            PreviewPoint(left, bottom),
        ),
    )

    private fun recognized(snapshot: OcrSnapshot) =
        OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot)

    private fun frozenSnapshot(rotation: Int): OcrSnapshot {
        val corners = when (rotation) {
            90 -> listOf(TextPoint(3, 1), TextPoint(3, 3), TextPoint(2, 3), TextPoint(2, 1))
            180 -> listOf(TextPoint(7, 3), TextPoint(5, 3), TextPoint(5, 2), TextPoint(7, 2))
            270 -> listOf(TextPoint(1, 7), TextPoint(1, 5), TextPoint(2, 5), TextPoint(2, 7))
            else -> listOf(TextPoint(1, 1), TextPoint(3, 1), TextPoint(3, 2), TextPoint(1, 2))
        }
        val element = TextElement("foobar", null, corners)
        return OcrSnapshot(
            text = "foobar",
            lines = listOf(TextLine("foobar", null, emptyList(), listOf(element))),
            frame = FrameInfo(
                timestampNanos = 0L,
                bufferWidth = 8,
                bufferHeight = 4,
                rotationDegrees = rotation,
                crop = TextBounds(1, 1, 7, 3),
                sensorToBufferMatrix = emptyList(),
            ),
        )
    }

    private fun invalidGeometrySnapshot(): OcrSnapshot = OcrSnapshot(
        text = "foo",
        lines = listOf(
            TextLine(
                "foo",
                null,
                emptyList(),
                listOf(TextElement("foo", null, listOf(
                    TextPoint(1, 1), TextPoint(3, 3), TextPoint(1, 3), TextPoint(3, 1),
                ))),
            ),
        ),
        frame = FrameInfo(0L, 8, 4, 0, TextBounds(0, 0, 8, 4), emptyList()),
    )

    private fun importedSnapshot() = ImportedTextSnapshot(
        text = "target",
        lines = listOf(
            TextLine(
                "target",
                null,
                emptyList(),
                listOf(
                    TextElement(
                        "target",
                        TextBounds(1, 1, 3, 2),
                        listOf(TextPoint(1, 1), TextPoint(3, 1),
                            TextPoint(3, 2), TextPoint(1, 2)),
                    ),
                ),
            ),
        ),
        width = 8,
        height = 4,
        sourceIdentity = 11L,
    )

    private data class OutlineBoundsExpected(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )

    private fun com.artkachenko.livetextfinder.search.MatchOutlineBounds.toExpected() =
        OutlineBoundsExpected(left, top, right, bottom)
}
