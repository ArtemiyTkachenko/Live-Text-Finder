package com.artkachenko.livetextfinder.camera

import android.graphics.Bitmap
import com.artkachenko.livetextfinder.ocr.CapturedCameraFrame
import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.search.FrameIdentity
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class FrozenFrameOwnerBoundaryTest {
    @Test
    fun lateCopyCannotBeatDelayedTimeoutRunnable() {
        val owner = FrozenFrameOwner()
        val canceled = mutableListOf<Long>()
        var requested = 0L
        owner.attachSession(
            requestCapture = {
                requested = it
                true
            },
            cancelCapture = { canceled.add(it) },
        )
        assertTrue(owner.freeze(cameraReady = true))
        val captured = frame(requested)
        // Move the clock without delivering the queued timeout callback.
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))

        assertFalse(owner.accept(captured))

        assertEquals(FreezeMode.Live, owner.state.value.mode)
        assertTrue(owner.state.value.error!!.contains("timed out"))
        assertNull(owner.currentBitmap())
        assertEquals(listOf(requested), canceled)
        owner.stop()
        captured.bitmap.recycle()
    }

    @Test
    fun mismatchedCopiedDimensionsCannotPublishFrozenSnapshot() {
        val owner = FrozenFrameOwner()
        var requested = 0L
        owner.attachSession(
            requestCapture = {
                requested = it
                true
            },
            cancelCapture = {
            },
        )
        assertTrue(owner.freeze(cameraReady = true))
        val captured = frame(requested).let { value ->
            val invalidFrame = value.snapshot.frame.copy(bufferWidth = 9)
            value.copy(snapshot = value.snapshot.copy(frame = invalidFrame))
        }

        assertFalse(owner.accept(captured))

        assertEquals(FreezeMode.Live, owner.state.value.mode)
        assertTrue(owner.state.value.error!!.contains("do not match"))
        assertNull(owner.currentBitmap())
        captured.bitmap.recycle()
    }

    private fun frame(requestId: Long): CapturedCameraFrame = CapturedCameraFrame(
        requestId = requestId,
        bitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888),
        snapshot = OcrSnapshot(
            text = "",
            lines = emptyList(),
            frame = FrameInfo(
                timestampNanos = 42L,
                bufferWidth = 8,
                bufferHeight = 4,
                rotationDegrees = 90,
                crop = TextBounds(0, 0, 8, 4),
                sensorToBufferMatrix = emptyList(),
            ),
        ),
        identity = FrameIdentity(12, 3),
    )
}
