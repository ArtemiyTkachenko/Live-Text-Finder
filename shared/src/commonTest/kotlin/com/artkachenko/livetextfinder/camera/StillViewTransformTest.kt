package com.artkachenko.livetextfinder.camera

import com.artkachenko.livetextfinder.search.PreviewPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StillViewTransformTest {
    @Test
    fun zoomKeepsCentroidAnchoredAndPanClampsToFittedImage() {
        val fit = FrozenFrameFit(100, 50, 0, 200f, 150f)
        val anchor = PreviewPoint(80f, 80f)
        val zoomed = StillViewTransform().zoomBy(2f, anchor, 200f, 150f, fit)

        assertEquals(PreviewPoint(80f, 80f), zoomed.map(anchor, 200f, 150f))
        assertEquals(StillViewTransform(2f, 20f, -5f), zoomed)
        val clamped = zoomed.panBy(1_000f, -1_000f, 200f, 150f, fit)
        assertEquals(100f, clamped.panX)
        assertEquals(-25f, clamped.panY)
    }

    @Test
    fun zoomLimitsResetAndInvalidInputsStayFinite() {
        val fit = FrozenFrameFit(80, 40, 0, 160f, 120f)
        val anchor = PreviewPoint(80f, 60f)
        val maxed = StillViewTransform().zoomBy(20f, anchor, 160f, 120f, fit)
        assertEquals(4f, maxed.zoom)
        assertTrue(maxed.panX.isFinite() && maxed.panY.isFinite())
        assertEquals(StillViewTransform(), maxed.reset())
        val minned = maxed.zoomBy(0.01f, anchor, 160f, 120f, fit)
        assertEquals(StillViewTransform(), minned)

        val invalid = StillViewTransform(Float.NaN, Float.POSITIVE_INFINITY, 0f)
        assertTrue(invalid.zoomBy(2f, anchor, 160f, 120f, fit).isRenderable)
        assertTrue(
            StillViewTransform().zoomBy(
                Float.NaN,
                anchor,
                160f,
                120f,
                fit,
            ).isRenderable,
        )
        assertNull(invalid.map(anchor, 160f, 120f))
        assertNull(StillViewTransform().map(PreviewPoint(Float.NaN, 0f), 160f, 120f))
    }

    @Test
    fun fitUsesUprightPortraitAndLandscapeDimensionsAndRejectsInvalidSizes() {
        val rotations = listOf(0, 90, 180, 270)
        rotations.forEach { rotation ->
            val fit = FrozenFrameFit(80, 48, rotation, 100f, 100f)
            assertTrue(fit.isValid)
            val expectedScale = minOf(
                100f / fit.uprightWidth,
                100f / fit.uprightHeight,
            )
            assertEquals(expectedScale, fit.scale)
            assertTrue(fit.uprightWidth * fit.scale <= 100f)
            assertTrue(fit.uprightHeight * fit.scale <= 100f)
        }
        assertTrue(!FrozenFrameFit(0, 48, 0, 100f, 100f).isValid)
        assertNull(
            FrozenFrameFit(80, 48, 45, 100f, 100f)
                .mapUprightPoint(PreviewPoint(1f, 1f)),
        )
        assertNotNull(FrozenFrameFit(80, 48, 0, 100f, 100f))
    }
}
