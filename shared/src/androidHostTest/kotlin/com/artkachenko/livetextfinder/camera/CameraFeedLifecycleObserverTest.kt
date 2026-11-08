package com.artkachenko.livetextfinder.camera

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CameraFeedLifecycleObserverTest {
    @Test
    fun pauseResumeRefreshesPermissionWithoutReplacingStartedFeed() {
        val owner = TestOwner()
        var epoch = 0
        var foreground = false
        var permissionReads = 0
        val observer = CameraFeedLifecycleObserver(
            onForegroundChanged = { foreground = it },
            onResume = { permissionReads++ },
            onStop = { epoch++ },
        )
        owner.lifecycle.addObserver(observer)
        owner.event(Lifecycle.Event.ON_CREATE)
        owner.event(Lifecycle.Event.ON_START)
        owner.event(Lifecycle.Event.ON_RESUME)
        assertTrue(foreground)
        assertEquals(1, permissionReads)
        owner.event(Lifecycle.Event.ON_PAUSE)
        assertTrue(foreground)
        owner.event(Lifecycle.Event.ON_RESUME)
        assertTrue(foreground)
        assertEquals(2, permissionReads)
        assertEquals(0, epoch)
        owner.lifecycle.removeObserver(observer)
    }

    @Test
    fun coalescedStopStartReplacesClosedFeedOnceWithoutRevivingRetiredObserver() {
        val owner = TestOwner()
        var epoch = 0
        var foreground = false
        var closes = 0
        var oldSessionEvents = 0
        val feedObserver = CameraFeedLifecycleObserver(
            onForegroundChanged = { foreground = it },
            onResume = {
            },
            onStop = { epoch++ },
        )
        lateinit var sessionObserver: CameraFeedLifecycleObserver
        sessionObserver = CameraFeedLifecycleObserver(
            onForegroundChanged = { oldSessionEvents++ },
            onResume = {
            },
            onStop = {
                closes++
                owner.lifecycle.removeObserver(sessionObserver)
            },
        )
        owner.lifecycle.addObserver(feedObserver)
        owner.lifecycle.addObserver(sessionObserver)
        owner.event(Lifecycle.Event.ON_CREATE)
        owner.event(Lifecycle.Event.ON_START)
        owner.event(Lifecycle.Event.ON_RESUME)
        owner.event(Lifecycle.Event.ON_PAUSE)
        owner.event(Lifecycle.Event.ON_STOP)
        assertFalse(foreground)
        assertEquals(1, closes)
        assertEquals(1, epoch)
        val retiredEvents = oldSessionEvents
        // No Compose frame is delivered between STOP and START.
        owner.event(Lifecycle.Event.ON_START)
        owner.event(Lifecycle.Event.ON_RESUME)
        assertTrue(foreground)
        assertEquals(1, epoch)
        assertEquals(1, closes)
        assertEquals(retiredEvents, oldSessionEvents)
        owner.event(Lifecycle.Event.ON_PAUSE)
        owner.event(Lifecycle.Event.ON_RESUME)
        assertTrue(foreground)
        assertEquals(1, epoch)
        owner.lifecycle.removeObserver(feedObserver)
    }

    private class TestOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)

        fun event(event: Lifecycle.Event) {
            lifecycle.handleLifecycleEvent(event)
        }
    }
}
