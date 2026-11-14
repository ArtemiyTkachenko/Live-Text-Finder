package com.artkachenko.livetextfinder.camera

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/** Observed camera capabilities and state for one native camera lease. */
internal data class LiveCaptureState(
    val lease: Long = 0L,
    val ready: Boolean = false,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val zoom: Float = 1f,
    val zoomPending: Boolean = false,
    val hasFlash: Boolean = false,
    val torchOn: Boolean = false,
    val torchPending: Boolean = false,
    val hasFocus: Boolean = false,
    val focusing: Boolean = false,
    val focusSucceeded: Boolean? = null,
) {
    val hasZoom: Boolean
        get() = minZoom.isFinite() && maxZoom.isFinite() && zoom.isFinite() &&
            minZoom > 0f && maxZoom > minZoom && zoom in minZoom..maxZoom
    val hasControls: Boolean
        get() = hasZoom || hasFlash || hasFocus
}

internal interface LiveCaptureActions {
    fun zoom(ratio: Float)
    fun torch(enabled: Boolean)
    fun focus()
}

/** Main-thread confined owner. Every UI action must retain its originating lease. */
internal class LiveCaptureControls(
    private val isContextCurrent: () -> Boolean,
    private val isCaptureContextCurrent: () -> Boolean,
) {
    private val mutableState = mutableStateOf(LiveCaptureState())
    val state: State<LiveCaptureState> = mutableState
    private var nextLease = 0L
    private var actions: LiveCaptureActions? = null
    private var onDetach: (() -> Unit)? = null
    private var requestedZoom: Float? = null
    private var pinchTarget: Float? = null
    private var queuedPinchTarget: Float? = null

    fun attach(actions: LiveCaptureActions, onDetach: () -> Unit): Long {
        detach(state.value.lease)
        val lease = ++nextLease
        this.actions = actions
        this.onDetach = onDetach
        mutableState.value = LiveCaptureState(lease = lease)
        return lease
    }

    fun isCurrent(lease: Long): Boolean = lease != 0L && state.value.lease == lease

    fun canDispatch(lease: Long): Boolean =
        isCurrent(lease) && state.value.ready && !state.value.zoomPending && isContextCurrent()

    fun canCapture(): Boolean = state.value.lease != 0L &&
        !state.value.zoomPending && isCaptureContextCurrent()

    fun update(lease: Long, transform: (LiveCaptureState) -> LiveCaptureState) {
        if (isCurrent(lease)) {
            mutableState.value = transform(state.value).copy(lease = lease)
            if (!canPinch(lease)) cancelPinch(lease)
        }
    }

    fun detach(lease: Long) {
        if (!isCurrent(lease)) return
        // Cleanup runs while ownership is still current, before a replacement can attach.
        try {
            onDetach?.invoke()
        } finally {
            cancelPinch(lease)
            requestedZoom = null
            onDetach = null
            actions = null
            mutableState.value = LiveCaptureState()
        }
    }

    fun zoomIn(lease: Long) = changeZoom(lease, 1.25f)

    fun zoomOut(lease: Long) = changeZoom(lease, 0.8f)

    fun canPinch(lease: Long): Boolean =
        isCurrent(lease) && state.value.ready && state.value.hasZoom && isContextCurrent()

    fun beginPinch(lease: Long): Boolean {
        if (!canPinch(lease)) return false
        val pending = requestedZoom.takeIf { state.value.zoomPending }
        pinchTarget = queuedPinchTarget ?: pending ?: state.value.zoom
        return true
    }

    fun scalePinch(lease: Long, factor: Float): Boolean {
        if (!canPinch(lease)) {
            cancelPinch(lease)
            return false
        }
        val previous = pinchTarget ?: return false
        if (!factor.isFinite() || factor <= 0f) return false
        val current = state.value
        val target = (previous * factor).coerceIn(current.minZoom, current.maxZoom)
        pinchTarget = target
        queuedPinchTarget = target
        dispatchQueuedPinch(lease)
        return true
    }

    fun endPinch(lease: Long) {
        if (isCurrent(lease)) pinchTarget = null
    }

    fun cancelPinch(lease: Long) {
        if (!isCurrent(lease)) return
        pinchTarget = null
        queuedPinchTarget = null
    }

    /** Drains at most one target after the native request completes and updates observed zoom. */
    fun dispatchQueuedPinch(lease: Long) {
        if (!canPinch(lease)) {
            cancelPinch(lease)
            return
        }
        if (state.value.zoomPending) return
        val target = queuedPinchTarget ?: return
        queuedPinchTarget = null
        requestZoom(lease, target)
    }

    private fun changeZoom(lease: Long, factor: Float) {
        requestZoom(lease, state.value.zoom * factor)
    }

    private fun requestZoom(lease: Long, ratio: Float) {
        val current = state.value
        if (!canDispatch(lease) || !current.hasZoom || !ratio.isFinite()) return
        val target = ratio.coerceIn(current.minZoom, current.maxZoom)
        if (target != current.zoom) {
            requestedZoom = target
            actions?.zoom(target)
        }
    }

    fun toggleTorch(lease: Long) {
        val current = state.value
        if (canDispatch(lease) && current.hasFlash && !current.torchPending) {
            actions?.torch(!current.torchOn)
        }
    }

    fun focusCenter(lease: Long) {
        val current = state.value
        if (canDispatch(lease) && current.hasFocus && !current.focusing) {
            actions?.focus()
        }
    }
}
