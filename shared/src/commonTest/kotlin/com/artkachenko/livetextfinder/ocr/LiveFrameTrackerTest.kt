package com.artkachenko.livetextfinder.ocr

import com.artkachenko.livetextfinder.search.PreviewTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LiveFrameTrackerTest {
    private var now = 100L
    private val identity = listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private val target = PreviewTarget(400, 200, identity)
    private fun running(session: Long = 1) = LiveFrameTracker(session) { now }.apply { setRunning(true); updateTarget(target) }
    private fun result(timestamp: Long = 1) = OcrUiState(OcrStatus.TextRecognized, snapshot = OcrSnapshot("foo", emptyList(),
        FrameInfo(timestamp, 400, 200, 0, TextBounds(0, 0, 400, 200), identity)))

    @Test fun exactFrameAndSessionGenerationStayPaired() {
        val frames = running()
        val result = result()
        val ticket = frames.ticket()
        assertTrue(frames.receive(ticket, result))
        assertSame(result.snapshot, frames.state.snapshot)
        assertSame(result.snapshot, frames.state.projection?.snapshot)
        assertEquals(ticket, frames.state.projection?.identity)
    }

    @Test fun duplicateAndOutOfOrderFramesNeverReplaceNewestFrame() {
        val frames = running()
        val newest = result(10)
        assertTrue(frames.receive(frames.ticket(), newest))
        assertFalse(frames.receive(frames.ticket(), result(9)))
        assertFalse(frames.receive(frames.ticket(), result(10)))
        assertSame(newest.snapshot, frames.state.snapshot)
    }

    @Test fun layoutTransformAndExplicitRotationInvalidateBeforeAnyNextCallback() {
        val frames = running()
        frames.receive(frames.ticket(), result())
        val old = frames.ticket()
        frames.updateTarget(target.copy(width = 200, height = 400))
        assertNull(frames.state.projection)
        assertFalse(frames.receive(old, result(2)))
        assertTrue(frames.receive(frames.ticket(), result(3)))
        assertEquals(200, frames.state.projection?.target?.width)
        val oldRotation = frames.ticket()
        frames.invalidateGeometry()
        assertNull(frames.state.projection)
        assertFalse(frames.receive(oldRotation, result(4)))
    }

    @Test fun unavailableTargetKeepsFreshCountSourceButNeverRestoresOldProjection() {
        val frames = running()
        frames.receive(frames.ticket(), result())
        frames.updateTarget(null)
        assertNotNull(frames.state.snapshot)
        assertNull(frames.state.projection)
        assertTrue(frames.receive(frames.ticket(), result(2)))
        assertNull(frames.state.projection)
        val beforeReady = frames.ticket()
        frames.updateTarget(target)
        assertNull(frames.state.projection)
        assertFalse(frames.receive(beforeReady, result(3)))
        assertTrue(frames.receive(frames.ticket(), result(4)))
        assertNotNull(frames.state.projection)
    }

    @Test fun changedMatrixInvalidatesButIdenticalTargetDoesNotCreateARejectionLoop() {
        val frames = running()
        val ticket = frames.ticket()
        repeat(10) { frames.updateTarget(target.copy(sensorToView = identity.toList())) }
        assertEquals(ticket, frames.ticket())
        assertTrue(frames.receive(ticket, result()))
        frames.updateTarget(target.copy(sensorToView = listOf(1f, 0f, 10f, 0f, 1f, 0f, 0f, 0f, 1f)))
        assertNull(frames.state.projection)
        assertFalse(frames.receive(ticket, result(2)))
    }

    @Test fun stoppedPermissionIdleAndZeroViewTransitionsClearAndRequireFreshResult() {
        val frames = running()
        frames.receive(frames.ticket(), result())
        val old = frames.ticket()
        frames.setRunning(false)
        assertNull(frames.state.snapshot)
        assertNull(frames.state.projection)
        assertFalse(frames.receive(old, result(2)))
        frames.setRunning(true)
        assertNull(frames.state.snapshot)
        assertFalse(frames.receive(old, result(3)))
        assertTrue(frames.receive(frames.ticket(), result(4)))
        assertEquals(4L, frames.state.snapshot?.frame?.timestampNanos)
    }

    @Test fun errorAndAllInactiveStatesCannotRetainSnapshotOrProjection() {
        listOf(OcrStatus.Error, OcrStatus.Unavailable, OcrStatus.PermissionRequired, OcrStatus.Paused, OcrStatus.Starting).forEach { status ->
            val frames = running()
            frames.receive(frames.ticket(), result())
            val old = frames.ticket()
            assertTrue(frames.receive(old, result(2).copy(status = status)))
            assertFalse(frames.receive(old, result(3)))
            assertNull(frames.state.snapshot)
            assertNull(frames.state.projection)
            assertNull(frames.millisUntilExpiry())
            assertEquals(status, frames.state.status)
            assertTrue(frames.receive(frames.ticket(), result(3)))
            assertNotNull(frames.state.projection)
        }
    }

    @Test fun blankSnapshotReplacesPopulatedResultAndItsExactFrameProjection() {
        val frames = running()
        frames.receive(frames.ticket(), result())
        val blank = result(2).copy(status = OcrStatus.Scanning, snapshot = result(2).snapshot!!.copy(text = ""))
        frames.receive(frames.ticket(), blank)
        assertSame(blank.snapshot, frames.state.snapshot)
        assertSame(blank.snapshot, frames.state.projection?.snapshot)
    }

    @Test fun receiptAgeBeforeAtAndAfter1500MillisExpiresWithoutSensorClockComparison() {
        val frames = running()
        frames.receive(frames.ticket(), result(Long.MAX_VALUE))
        now = 1599
        frames.expire()
        assertNotNull(frames.state.snapshot)
        assertEquals(1L, frames.millisUntilExpiry())
        now = 1600
        frames.expire()
        assertNull(frames.state.snapshot)
        assertNull(frames.state.projection)
        assertNull(frames.millisUntilExpiry())
        now = 1601
        frames.expire()
        assertNull(frames.state.snapshot)
    }

    @Test fun oldExpiryCannotClearNewResultAndFreshReceiptRestoresExpiredState() {
        val frames = running()
        frames.receive(frames.ticket(), result())
        now = 1500
        frames.receive(frames.ticket(), result(2))
        now = 1600
        frames.expire() // formerly scheduled deadline of the first result
        assertEquals(2L, frames.state.snapshot?.frame?.timestampNanos)
        now = 3000
        frames.expire()
        assertNull(frames.state.snapshot)
        assertTrue(frames.receive(frames.ticket(), result(3)))
        assertNotNull(frames.state.projection)
    }

    @Test fun disposedAndRetiredSessionCallbacksAndExpiryNeverReviveCurrentState() {
        val old = running(1)
        val oldTicket = old.ticket()
        old.receive(oldTicket, result())
        old.dispose()
        old.setRunning(true)
        old.updateTarget(target)
        assertFalse(old.receive(oldTicket, result(2)))
        old.expire()
        assertNull(old.state.snapshot)
        val next = running(2)
        assertFalse(next.receive(oldTicket, result(3)))
        assertTrue(next.receive(next.ticket(), result(1))) // timestamps are scoped to a session
        old.expire()
        assertNotNull(next.state.projection)
    }
}
