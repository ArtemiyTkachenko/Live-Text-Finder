package com.artkachenko.livetextfinder.ocr

import com.artkachenko.livetextfinder.search.FrameIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraTimingDiagnosticsTest {
    @Test
    fun clockAndLogRuntimeFailuresAreIsolated() {
        var logCalls = 0
        val throwingClock = CameraTimingDiagnostics(
            nowNanos = { throw IllegalStateException("clock") },
            log = { logCalls++ },
        )

        assertEquals(null, throwingClock.clock())
        throwingClock.recognition(10L, 20L, 30L, false, false)
        throwingClock.frameAccepted(FrameIdentity(1L, 2L), null)
        throwingClock.frameRejected(FrameIdentity(1L, 1L), FrameIdentity(1L, 2L))
        assertEquals(0, logCalls)

        var throwingLogCalls = 0
        val throwingLog = CameraTimingDiagnostics(nowNanos = { 3_000_000_000L }) {
            throwingLogCalls++
            throw IllegalStateException("sink")
        }
        throwingLog.recognition(10L, 20L, 30L, false, false)
        throwingLog.frameAccepted(FrameIdentity(1L, 2L), null)
        assertEquals(1, throwingLogCalls)
    }

    @Test
    fun geometryChangeAfterErrorClassifiesOldCallbackAsIdentityMismatch() {
        val logs = mutableListOf<String>()
        val diagnostics = CameraTimingDiagnostics({ 3_000_000_000L }, logs::add)
        val tracker = LiveFrameTracker(4L, nowMillis = { 0L })
        tracker.setRunning(true)
        val received = tracker.ticket()

        assertTrue(tracker.receive(received, OcrUiState(OcrStatus.Error, "camera issue")))
        tracker.invalidateGeometry()
        assertFalse(tracker.receive(received, OcrUiState(OcrStatus.Scanning)))
        diagnostics.frameRejected(received, tracker.ticket())

        val report = logs.single()
        assertTrue(report.contains("identityMismatch=1 otherRejected=0"))
        assertTrue(report.contains("received=session=4,generation=1"))
        assertTrue(report.contains("current=session=4,generation=3"))
    }

    @Test
    fun aggregatesBoundedLiveAndCaptureSamplesSeparatelyAndRateLimitsOutput() {
        var now = 1_000_000_000L
        val logs = mutableListOf<String>()
        val diagnostics = CameraTimingDiagnostics({ now }, logs::add)

        repeat(70) { index ->
            diagnostics.recognition(index * 1_000_000L, 2_000_000L, 3_000_000L, false, false)
        }
        assertEquals(1, logs.size)
        assertTrue(logs.single().contains("samples=1"))
        now += 2_000_000_000L
        diagnostics.recognition(70_000_000L, 2_000_000L, 3_000_000L, false, false)
        assertTrue(logs.last().contains("live[n=64,ocr=n=64,p50=39ms,p95=67ms,max=70ms"))
        assertFalse(logs.last().contains("recognized"))

        now += 1_999_000_000L
        diagnostics.recognition(500_000_000L, null, 100_000_000L, true, true)
        diagnostics.frameRejected(FrameIdentity(4L, 2L), FrameIdentity(4L, 3L))
        diagnostics.frameRejected(FrameIdentity(4L, 3L), FrameIdentity(4L, 3L))
        assertEquals(2, logs.size)
        now += 1_000_000L
        diagnostics.frameAccepted(
            FrameIdentity(4L, 3L),
            FrameInfo(1L, 1280, 720, 270, TextBounds(0, 0, 1280, 720), emptyList()),
        )
        assertEquals(3, logs.size)
        assertTrue(
            logs.last().contains(
                "accepted=1 rejected=2 identityMismatch=1 otherRejected=1 errors=1",
            ),
        )
        assertTrue(logs.last().contains("liveCadence=2000ms captureCadence=n/a"))
        assertTrue(
            logs.last().contains(
                "capture[n=1,ocr=n=1,p50=500ms,p95=500ms,max=500ms,snapshot=n=0",
            ),
        )
        assertTrue(logs.last().contains("size=1280x720,rotation=270"))

        val freshLogs = mutableListOf<String>()
        CameraTimingDiagnostics({ now }, freshLogs::add)
            .frameAccepted(FrameIdentity(5L, 0L), null)
        assertTrue(freshLogs.single().contains("accepted=1 rejected=0"))
        assertTrue(freshLogs.single().contains("live[n=0"))
    }
}
