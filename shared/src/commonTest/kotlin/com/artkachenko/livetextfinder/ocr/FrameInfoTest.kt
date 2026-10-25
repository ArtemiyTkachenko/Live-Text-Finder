package com.artkachenko.livetextfinder.ocr

import kotlin.test.Test
import kotlin.test.assertEquals

class FrameInfoTest {
    @Test fun uprightDimensionsRespectAllCameraRotations() {
        listOf(0, 90, 180, 270).forEach { rotation ->
            val frame = FrameInfo(
                42, 1280, 720, rotation, TextBounds(0, 0, 1280, 720),
                listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            )
            assertEquals(if (rotation == 90 || rotation == 270) 720 else 1280, frame.uprightWidth)
            assertEquals(if (rotation == 90 || rotation == 270) 1280 else 720, frame.uprightHeight)
            assertEquals(42L, frame.timestampNanos)
        }
    }
}
