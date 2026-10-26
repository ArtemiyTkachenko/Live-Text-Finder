package com.artkachenko.livetextfinder.camera

import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.search.FrameIdentity
import com.artkachenko.livetextfinder.search.PreviewPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrozenFrameControllerTest {
    @Test
    fun fitMapsRawBufferToCenteredUprightViewportForEveryRotation() {
        val expected = mapOf(
            0 to listOf(PreviewPoint(0f, 2.5f), PreviewPoint(10f, 7.5f)),
            90 to listOf(PreviewPoint(7.5f, 0f), PreviewPoint(2.5f, 10f)),
            180 to listOf(PreviewPoint(10f, 7.5f), PreviewPoint(0f, 2.5f)),
            270 to listOf(PreviewPoint(2.5f, 10f), PreviewPoint(7.5f, 0f)),
        )

        expected.forEach { (rotation, points) ->
            val fit = FrozenFrameFit(4, 2, rotation, 10f, 10f)

            assertTrue(fit.isValid)
            assertEquals(points[0], fit.mapRawPoint(PreviewPoint(0f, 0f)))
            assertEquals(points[1], fit.mapRawPoint(PreviewPoint(4f, 2f)))
        }
    }

    @Test
    fun invalidRotationAndViewportCannotProduceCoordinates() {
        val invalidRotation = FrozenFrameFit(4, 2, 45, 10f, 10f)
        val invalidViewport = FrozenFrameFit(4, 2, 0, 0f, 10f)

        assertFalse(invalidRotation.isValid)
        assertFalse(invalidViewport.isValid)
        assertNull(invalidRotation.mapRawPoint(PreviewPoint(0f, 0f)))
        assertNull(invalidViewport.mapUprightPoint(PreviewPoint(0f, 0f)))
    }

    @Test
    fun captureIsSingleFlightAndTimeoutRejectsLateResults() {
        val published = mutableListOf<FreezePresentationState>()
        val controller = FrozenFrameController(published::add)

        assertFalse(controller.begin(1, 100, cameraReady = false))
        assertTrue(controller.begin(1, 100, cameraReady = true))
        assertFalse(controller.begin(2, 101, cameraReady = true))
        assertEquals(5_100L, controller.expire(5_099))
        assertNull(controller.expire(5_100))
        assertEquals(FreezeMode.Live, controller.state.mode)
        assertEquals("Freeze timed out. Retry when the camera is ready.", controller.state.error)
        assertFalse(controller.accept(frame(1), 5_100))
        assertEquals(FreezeMode.Freezing, published.first().mode)
        assertEquals(FreezeMode.Live, published.last().mode)
    }

    @Test
    fun acceptedFrameCanBeResumedAndStopDiscardsPendingOrFrozenState() {
        val controller = FrozenFrameController(
            publish = {
            },
        )

        assertTrue(controller.begin(5, 0, cameraReady = true))
        assertTrue(controller.accept(frame(5), 2))
        assertEquals(FreezeMode.Frozen, controller.state.mode)
        assertFalse(controller.begin(6, 1, cameraReady = true))
        assertTrue(controller.resume())
        assertEquals(FreezePresentationState(), controller.state)
        assertTrue(controller.begin(7, 2, cameraReady = true))
        assertTrue(controller.accept(frame(7), 3))
        assertEquals(FreezeMode.Frozen, controller.state.mode)
        controller.stop()
        assertEquals(FreezePresentationState(), controller.state)
    }

    @Test
    fun staleFailureAndWrongRequestCannotReplaceCurrentCapture() {
        val controller = FrozenFrameController(
            publish = {
            },
        )

        assertTrue(controller.begin(8, 0, cameraReady = true))
        assertFalse(controller.fail(7, "stale"))
        assertFalse(controller.accept(frame(7), 2))
        assertEquals(8L, controller.state.requestId)
        assertTrue(controller.cancel(8))
        assertFalse(controller.cancel(8))
        assertEquals(FreezePresentationState(), controller.state)
    }

    @Test
    fun delayedTimeoutCallbackCannotAllowLateCaptureToFreeze() {
        val controller = FrozenFrameController(
            publish = {
            },
        )

        assertTrue(controller.begin(9, 0, cameraReady = true))
        assertFalse(controller.accept(frame(9), 5_001))
        assertEquals(FreezeMode.Live, controller.state.mode)
        assertEquals("Freeze timed out. Retry when the camera is ready.", controller.state.error)
    }

    private fun frame(requestId: Long) = FrozenSearchFrame(
        requestId = requestId,
        identity = FrameIdentity(2, 3),
        snapshot = OcrSnapshot(
            text = "sample",
            lines = emptyList(),
            frame = FrameInfo(
                timestampNanos = 42,
                bufferWidth = 4,
                bufferHeight = 2,
                rotationDegrees = 90,
                crop = TextBounds(0, 0, 4, 2),
                sensorToBufferMatrix = listOf(
                    1f, 0f, 0f,
                    0f, 1f, 0f,
                    0f, 0f, 1f,
                ),
            ),
        ),
    )
}
