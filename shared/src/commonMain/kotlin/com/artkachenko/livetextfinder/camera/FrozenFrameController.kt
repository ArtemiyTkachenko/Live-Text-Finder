package com.artkachenko.livetextfinder.camera

/** Coordinates one capture request without owning platform image buffers. */
internal class FrozenFrameController(
    private val publish: (FreezePresentationState) -> Unit,
    private val timeoutMillis: Long = CAPTURE_TIMEOUT_MILLIS,
) {
    var state = FreezePresentationState()
        private set
    private var deadlineMillis: Long? = null

    fun begin(requestId: Long, nowMillis: Long, cameraReady: Boolean): Boolean {
        if (!cameraReady || state.mode != FreezeMode.Live || requestId <= 0L) {
            return false
        }
        deadlineMillis = nowMillis + timeoutMillis
        update(FreezePresentationState(FreezeMode.Freezing, requestId))
        return true
    }

    fun accept(frame: FrozenSearchFrame, nowMillis: Long): Boolean {
        expire(nowMillis)
        if (state.mode != FreezeMode.Freezing || state.requestId != frame.requestId) {
            return false
        }
        deadlineMillis = null
        update(FreezePresentationState(FreezeMode.Frozen, frame = frame))
        return true
    }

    fun fail(requestId: Long, message: String): Boolean {
        if (state.mode != FreezeMode.Freezing || state.requestId != requestId) {
            return false
        }
        deadlineMillis = null
        update(FreezePresentationState(error = message))
        return true
    }

    fun cancel(requestId: Long): Boolean {
        if (state.mode != FreezeMode.Freezing || state.requestId != requestId) {
            return false
        }
        deadlineMillis = null
        update(FreezePresentationState())
        return true
    }

    fun resume(): Boolean {
        if (state.mode != FreezeMode.Frozen) {
            return false
        }
        update(FreezePresentationState())
        return true
    }

    fun stop() {
        deadlineMillis = null
        update(FreezePresentationState())
    }

    fun expire(nowMillis: Long): Long? {
        val deadline = deadlineMillis ?: return null
        if (nowMillis < deadline) {
            return deadline
        }
        fail(checkNotNull(state.requestId), "Freeze timed out. Retry when the camera is ready.")
        return null
    }

    private fun update(value: FreezePresentationState) {
        state = value
        publish(value)
    }

    private companion object {
        const val CAPTURE_TIMEOUT_MILLIS = 5_000L
    }
}
