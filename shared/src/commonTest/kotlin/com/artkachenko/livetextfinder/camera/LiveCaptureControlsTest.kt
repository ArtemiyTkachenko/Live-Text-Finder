package com.artkachenko.livetextfinder.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveCaptureControlsTest {
    @Test
    fun staleUiActionsUpdatesAndDetachCannotTouchReplacementLease() {
        var context = true
        val owner = LiveCaptureControls({ context }, { context })
        val old = Actions()
        var cleanups = 0
        val first = owner.attach(old) { cleanups++ }
        owner.update(first) { supported(it) }
        val staleZoom = { owner.zoomIn(first) }
        owner.zoomIn(first)
        assertEquals(listOf(1.25f), old.zooms)
        val replacement = Actions()
        val second = owner.attach(replacement) { cleanups++ }
        owner.update(second) { supported(it) }
        assertEquals(1, cleanups)
        staleZoom()
        owner.toggleTorch(first)
        owner.focusCenter(first)
        owner.update(first) { it.copy(zoomPending = true) }
        owner.detach(first)
        assertTrue(owner.isCurrent(second))
        assertFalse(owner.state.value.zoomPending)
        assertTrue(replacement.zooms.isEmpty())
        context = false
        owner.zoomIn(second)
        owner.toggleTorch(second)
        owner.focusCenter(second)
        assertTrue(replacement.zooms.isEmpty())
        assertTrue(replacement.torches.isEmpty())
        assertEquals(0, replacement.focuses)
        context = true
        owner.toggleTorch(second)
        owner.focusCenter(second)
        assertEquals(listOf(true), replacement.torches)
        assertEquals(1, replacement.focuses)
        owner.detach(second)
        assertEquals(2, cleanups)
    }

    @Test
    fun unsupportedNonfiniteBoundaryAndPendingZoomCannotDispatch() {
        val owner = LiveCaptureControls({ true }, { true })
        val actions = Actions()
        val lease = owner.attach(actions) {
        }
        owner.zoomIn(lease)
        owner.toggleTorch(lease)
        owner.focusCenter(lease)
        assertTrue(actions.zooms.isEmpty())
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f)) {
            owner.update(lease) { supported(it).copy(minZoom = invalid) }
            owner.zoomIn(lease)
        }
        assertTrue(actions.zooms.isEmpty())
        owner.update(lease) { supported(it).copy(zoom = 2f) }
        owner.zoomIn(lease)
        assertTrue(actions.zooms.isEmpty())
        owner.zoomOut(lease)
        assertEquals(listOf(1.6f), actions.zooms)
        owner.update(lease) { it.copy(zoom = 1f) }
        owner.zoomOut(lease)
        assertEquals(1, actions.zooms.size)
        owner.update(lease) { it.copy(zoomPending = true) }
        owner.zoomIn(lease)
        owner.toggleTorch(lease)
        owner.focusCenter(lease)
        assertEquals(1, actions.zooms.size)
        assertTrue(actions.torches.isEmpty())
        assertEquals(0, actions.focuses)
    }

    @Test
    fun captureSourceSurvivesFreezingWhileNewAdjustmentsAreDisabled() {
        var live = true
        var sourceCurrent = true
        val owner = LiveCaptureControls({ live && sourceCurrent }, { sourceCurrent })
        val actions = Actions()
        val lease = owner.attach(actions) {
        }
        owner.update(lease) { supported(it) }
        assertTrue(owner.canCapture())
        live = false
        assertFalse(owner.canDispatch(lease))
        assertTrue(owner.canCapture())
        owner.zoomIn(lease)
        owner.toggleTorch(lease)
        assertTrue(actions.zooms.isEmpty())
        assertTrue(actions.torches.isEmpty())
        sourceCurrent = false
        assertFalse(owner.canCapture())
        sourceCurrent = true
        owner.update(lease) { it.copy(zoomPending = true) }
        assertFalse(owner.canCapture())
        owner.update(lease) { it.copy(zoomPending = false) }
        assertTrue(owner.canCapture())
        owner.detach(lease)
        assertFalse(owner.canCapture())
    }

    @Test
    fun pinchCoalescesLatestTargetAndDrainsAfterFingersLift() {
        val owner = LiveCaptureControls({ true }, { true })
        val actions = Actions()
        val lease = owner.attach(actions) {}
        owner.update(lease) { supported(it) }
        assertTrue(owner.beginPinch(lease))
        assertTrue(owner.scalePinch(lease, 1.25f))
        owner.update(lease) { it.copy(zoomPending = true) }
        assertTrue(owner.scalePinch(lease, 1.2f))
        assertTrue(owner.scalePinch(lease, 4f))
        assertTrue(owner.scalePinch(lease, 0.875f))
        owner.endPinch(lease)
        owner.dispatchQueuedPinch(lease)
        assertEquals(listOf(1.25f), actions.zooms)

        owner.update(lease) { it.copy(zoom = 1.25f, zoomPending = false) }
        owner.dispatchQueuedPinch(lease)
        owner.dispatchQueuedPinch(lease)
        assertEquals(listOf(1.25f, 1.75f), actions.zooms)
    }

    @Test
    fun pinchDropsQueuedTargetWhenAreaEditingOrLeaseChanges() {
        var areaEditing = false
        val owner = LiveCaptureControls({ !areaEditing }, { true })
        val actions = Actions()
        val lease = owner.attach(actions) {}
        owner.update(lease) { supported(it) }
        assertTrue(owner.beginPinch(lease))
        assertFalse(owner.scalePinch(lease, Float.NaN))
        owner.update(lease) { it.copy(zoomPending = true) }
        assertTrue(owner.scalePinch(lease, 1.5f))
        areaEditing = true
        assertFalse(owner.scalePinch(lease, 1.1f))
        owner.update(lease) { it.copy(zoomPending = false) }
        owner.dispatchQueuedPinch(lease)
        areaEditing = false
        owner.dispatchQueuedPinch(lease)
        assertTrue(actions.zooms.isEmpty())

        assertTrue(owner.beginPinch(lease))
        owner.update(lease) { it.copy(zoomPending = true) }
        assertTrue(owner.scalePinch(lease, 1.5f))
        val replacement = Actions()
        val next = owner.attach(replacement) {}
        owner.update(next) { supported(it) }
        assertFalse(owner.scalePinch(lease, 1.5f))
        owner.dispatchQueuedPinch(lease)
        owner.dispatchQueuedPinch(next)
        assertTrue(replacement.zooms.isEmpty())
    }

    private fun supported(state: LiveCaptureState) = state.copy(
        ready = true, minZoom = 1f, maxZoom = 2f, zoom = 1f,
        hasFlash = true, hasFocus = true,
    )

    private class Actions : LiveCaptureActions {
        val zooms = mutableListOf<Float>()
        val torches = mutableListOf<Boolean>()
        var focuses = 0
        override fun zoom(ratio: Float) {
            zooms += ratio
        }
        override fun torch(enabled: Boolean) {
            torches += enabled
        }
        override fun focus() {
            focuses++
        }
    }
}
