package com.artkachenko.livetextfinder.ocr

import android.util.Log
import com.artkachenko.livetextfinder.search.FrameIdentity

/** Debug-only bounded timing and frame acceptance diagnostics. */
internal class CameraTimingDiagnostics(
    private val nowNanos: () -> Long = System::nanoTime,
    private val log: (String) -> Unit = { message -> Log.d(TAG, message) },
) {
    private val samples = ArrayDeque<TimingSample>()
    private var accepted = 0L
    private var rejected = 0L
    private var identityMismatch = 0L
    private var errors = 0L
    private var lastLiveNanos: Long? = null
    private var lastCaptureNanos: Long? = null
    private var liveCadenceMillis: Long? = null
    private var captureCadenceMillis: Long? = null
    private var lastLogNanos: Long? = null
    private var latest = "metadata=unknown"

    fun clock(): Long? = try {
        nowNanos()
    } catch (_: RuntimeException) {
        null
    }

    fun recognition(
        ocrNanos: Long?,
        snapshotNanos: Long?,
        deliveryNanos: Long?,
        failed: Boolean,
        isCapture: Boolean,
    ) {
        observe {
            samples.addLast(TimingSample(isCapture, ocrNanos, snapshotNanos, deliveryNanos))
            if (samples.size > MAX_SAMPLES) samples.removeFirst()
            if (failed) errors++
            updateCadence(isCapture, clock())
            emit()
        }
    }

    fun frameAccepted(identity: FrameIdentity, metadata: FrameInfo?) {
        observe {
            accepted++
            latest = "${identitySummary(identity)} ${metadataSummary(metadata)}"
            emit()
        }
    }

    fun frameRejected(
        identity: FrameIdentity,
        current: FrameIdentity,
        metadata: FrameInfo? = null,
    ) {
        observe {
            rejected++
            if (identity != current) identityMismatch++
            latest = "received=${identitySummary(identity)} current=${identitySummary(current)} " +
                metadataSummary(metadata)
            emit()
        }
    }

    private fun updateCadence(isCapture: Boolean, now: Long?) {
        if (now == null) return
        if (isCapture) {
            lastCaptureNanos?.let { captureCadenceMillis = elapsed(now, it) / 1_000_000L }
            lastCaptureNanos = now
        } else {
            lastLiveNanos?.let { liveCadenceMillis = elapsed(now, it) / 1_000_000L }
            lastLiveNanos = now
        }
    }

    private fun emit() {
        val now = clock() ?: return
        val previousLog = lastLogNanos
        if (previousLog != null && now - previousLog < LOG_INTERVAL_NANOS) return
        lastLogNanos = now
        val live = samples.filterNot { it.isCapture }
        val capture = samples.filter { it.isCapture }
        log(
            "samples=${samples.size} accepted=$accepted rejected=$rejected " +
                "identityMismatch=$identityMismatch otherRejected=${rejected - identityMismatch} " +
                "errors=$errors liveCadence=${cadence(liveCadenceMillis)} " +
                "captureCadence=${cadence(captureCadenceMillis)} " +
                "live[${stageStats(live)}] capture[${stageStats(capture)}] $latest",
        )
    }

    private fun stageStats(values: List<TimingSample>): String =
        "n=${values.size},ocr=${stats(values.mapNotNull { it.ocrNanos })}," +
            "snapshot=${stats(values.mapNotNull { it.snapshotNanos })}," +
            "delivery=${stats(values.mapNotNull { it.deliveryNanos })}"

    private fun stats(values: List<Long>): String {
        if (values.isEmpty()) return "n=0"
        val sorted = values.sorted()
        return "n=${sorted.size},p50=${percentile(sorted, 50) / 1_000_000}ms," +
            "p95=${percentile(sorted, 95) / 1_000_000}ms,max=${sorted.last() / 1_000_000}ms"
    }

    private fun identitySummary(identity: FrameIdentity): String =
        "session=${identity.session},generation=${identity.generation}"

    private fun metadataSummary(metadata: FrameInfo?): String = metadata?.let {
        "size=${it.bufferWidth}x${it.bufferHeight},rotation=${it.rotationDegrees}"
    } ?: "metadata=unavailable"

    private fun cadence(value: Long?): String = value?.let { "${it}ms" } ?: "n/a"

    private inline fun observe(action: () -> Unit) {
        try {
            action()
        } catch (_: RuntimeException) {
            // Diagnostics are observational and must not interrupt camera ownership or delivery.
        }
    }

    private data class TimingSample(
        val isCapture: Boolean,
        val ocrNanos: Long?,
        val snapshotNanos: Long?,
        val deliveryNanos: Long?,
    )

    private companion object {
        const val MAX_SAMPLES = 64
        const val LOG_INTERVAL_NANOS = 2_000_000_000L
        const val TAG = "LTF.CameraTiming"

        fun elapsed(end: Long, start: Long): Long = (end - start).coerceAtLeast(0L)

        fun percentile(values: List<Long>, percentile: Int): Long =
            values[((values.size - 1) * percentile + 99) / 100]
    }
}
