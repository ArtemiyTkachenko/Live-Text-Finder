package com.artkachenko.livetextfinder.camera

import android.graphics.Bitmap
import com.artkachenko.livetextfinder.ocr.CapturedCameraFrame
import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.search.FrameIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class AndroidFrozenFrameOwnerTest {
    @Test
    fun acceptedPixelsAndSnapshotSurviveSessionReplacementUntilResume() {
        val owner = FrozenFrameOwner()
        var requestId: Long? = null
        owner.attachSession(
            requestCapture = {
                requestId = it
                true
            },
            cancelCapture = {
            },
        )

        assertTrue(owner.freeze(cameraReady = true))
        val bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        val capture = CapturedCameraFrame(
            requestId = assertNotNullValue(requestId),
            bitmap = bitmap,
            snapshot = snapshot(),
            identity = FrameIdentity(1, 3),
        )

        assertTrue(owner.accept(capture))
        assertEquals(FreezeMode.Frozen, owner.state.value.mode)
        assertSame(bitmap, owner.currentBitmap())
        assertSame(capture.snapshot, owner.state.value.frame?.snapshot)
        owner.detachSession()

        assertEquals(FreezeMode.Frozen, owner.state.value.mode)
        assertSame(bitmap, owner.currentBitmap())
        owner.resume()
        assertEquals(FreezePresentationState(), owner.state.value)
        assertNull(owner.currentBitmap())
        bitmap.recycle()
    }

    @Test
    fun sessionStopCancelsPendingCaptureAndRejectsLateResult() {
        val owner = FrozenFrameOwner()
        var requestId: Long? = null
        var cancelled: Long? = null
        owner.attachSession(
            requestCapture = {
                requestId = it
                true
            },
            cancelCapture = { cancelled = it },
        )

        assertTrue(owner.freeze(cameraReady = true))
        val pendingId = assertNotNullValue(requestId)
        owner.stop()
        val bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        val lateCapture = CapturedCameraFrame(
            requestId = pendingId,
            bitmap = bitmap,
            snapshot = snapshot(),
            identity = FrameIdentity(1, 3),
        )

        assertEquals(pendingId, cancelled)
        assertEquals(FreezePresentationState(), owner.state.value)
        assertFalse(owner.accept(lateCapture))
        assertNull(owner.currentBitmap())
        bitmap.recycle()
    }

    @Test
    fun captureRequestNeedsAnAttachedReadyCamera() {
        val owner = FrozenFrameOwner()

        assertFalse(owner.freeze(cameraReady = true))
        assertFalse(owner.freeze(cameraReady = false))
        assertEquals(FreezePresentationState(), owner.state.value)
    }

    private fun snapshot() = OcrSnapshot(
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
    )

    private fun assertNotNullValue(value: Long?): Long {
        assertTrue(value != null)
        return checkNotNull(value)
    }
}
