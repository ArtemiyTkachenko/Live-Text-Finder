package com.artkachenko.livetextfinder.ocr

import com.artkachenko.livetextfinder.search.FrameIdentity
import com.artkachenko.livetextfinder.search.FrameProjection
import com.artkachenko.livetextfinder.search.PreviewTarget
import com.artkachenko.livetextfinder.utils.MonotonicClock

/** Main-thread owner supplies monotonic receipt time; one session never revives after disposal. */
internal class LiveFrameTracker(
    private val session: Long,
    private val nowMillis: () -> Long = MonotonicClock::nowMillis,
) {
    private var generation = 0L
    private var running = false
    private var disposed = false
    private var target: PreviewTarget? = null
    private var lastTimestamp: Long? = null
    private var receivedAt: Long? = null
    var state = OcrUiState(OcrStatus.Starting, "Waiting for camera preview…")
        private set

    fun ticket() = FrameIdentity(session, generation)

    fun setRunning(value: Boolean) {
        if (disposed || running == value) return
        running = value
        generation++
        receivedAt = null
        state = OcrUiState(if (value) OcrStatus.Scanning else OcrStatus.Paused,
            if (value) "Waiting for fresh text results…" else "Camera preview is not active.")
    }

    fun updateTarget(value: PreviewTarget?) {
        if (disposed || target == value) return
        target = value
        generation++
        state = state.copy(projection = null)
    }

    /** Explicit target-rotation changes invalidate even before a new target matrix arrives. */
    fun invalidateGeometry() {
        if (disposed) return
        generation++
        target = null
        state = state.copy(projection = null)
    }

    fun receive(identity: FrameIdentity, result: OcrUiState): Boolean {
        if (disposed || !running || identity != ticket()) return false
        val snapshot = result.snapshot
        if (result.status != OcrStatus.Scanning && result.status != OcrStatus.TextRecognized || snapshot == null) {
            receivedAt = null
            generation++
            state = result.copy(snapshot = null, projection = null)
            return true
        }
        if (lastTimestamp?.let { snapshot.frame.timestampNanos <= it } == true) return false
        lastTimestamp = snapshot.frame.timestampNanos
        val receipt = nowMillis()
        receivedAt = receipt
        val projection = target?.let { FrameProjection(snapshot, identity, it) }
        state = result.copy(projection = projection, receivedAtMillis = receipt)
        return true
    }

    fun millisUntilExpiry(): Long? {
        return receivedAt?.let { (RESULT_AGE_MILLIS - (nowMillis() - it)).coerceAtLeast(0) }
    }

    fun expire() {
        if (disposed) return
        if (receivedAt?.let { nowMillis() - it >= RESULT_AGE_MILLIS } == true) {
            receivedAt = null
            state = OcrUiState(OcrStatus.Scanning, "Waiting for fresh text results (previous result expired)…")
        }
    }

    fun dispose() {
        disposed = true
        running = false
        receivedAt = null
        generation++
        target = null
        state = OcrUiState(OcrStatus.Paused, "Camera session ended.")
    }

    companion object { const val RESULT_AGE_MILLIS = 1_500L }
}
