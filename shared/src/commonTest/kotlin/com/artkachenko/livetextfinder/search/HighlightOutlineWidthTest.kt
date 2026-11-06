package com.artkachenko.livetextfinder.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HighlightOutlineWidthTest {
    @Test
    fun wideQuadUsesMaximumAndSixPixelHeightUsesTwelvePercent() {
        assertEquals(2f, highlightOutlineWidth(rectangle(0f, 0f, 100f, 40f), 2f))
        assertEquals(0.72f, highlightOutlineWidth(rectangle(0f, 0f, 100f, 6f), 2f))
    }

    @Test
    fun rotatedAndVerticalQuadsKeepTheSameMinimumSpanWidth() {
        val rotated = listOf(
            PreviewPoint(0f, 0f),
            PreviewPoint(60f, 80f),
            PreviewPoint(55.2f, 83.6f),
            PreviewPoint(-4.8f, 3.6f),
        )
        assertEquals(0.72f, checkNotNull(highlightOutlineWidth(rotated, 2f)), 0.0001f)
        assertEquals(0.72f, highlightOutlineWidth(rectangle(0f, 0f, 6f, 100f), 2f))
    }

    @Test
    fun skewedAndSubpixelQuadsKeepTheirProportionalWidth() {
        val skewed = listOf(
            PreviewPoint(0f, 0f),
            PreviewPoint(100f, 10f),
            PreviewPoint(99f, 16f),
            PreviewPoint(-1f, 6f),
        )
        assertEquals(0.72f, checkNotNull(highlightOutlineWidth(skewed, 2f)), 0.01f)
        assertEquals(
            0.012f,
            checkNotNull(highlightOutlineWidth(rectangle(0f, 0f, 4f, 0.1f), 2f)),
            0.0001f,
        )
    }

    @Test
    fun invalidGeometryAndNonpositiveMaximumHaveNoStrokeWidth() {
        assertNull(highlightOutlineWidth(emptyList(), 2f))
        assertNull(highlightOutlineWidth(rectangle(0f, 0f, 0f, 6f), 2f))
        assertNull(highlightOutlineWidth(rectangle(0f, 0f, 100f, 6f), 0f))
        assertNull(highlightOutlineWidth(rectangle(0f, 0f, 100f, 6f), Float.NaN))
    }

    private fun rectangle(left: Float, top: Float, right: Float, bottom: Float) = listOf(
        PreviewPoint(left, top),
        PreviewPoint(right, top),
        PreviewPoint(right, bottom),
        PreviewPoint(left, bottom),
    )
}
