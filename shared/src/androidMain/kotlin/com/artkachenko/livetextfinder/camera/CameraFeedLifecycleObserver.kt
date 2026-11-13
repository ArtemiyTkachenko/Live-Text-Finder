package com.artkachenko.livetextfinder.camera

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** Distinguishes a stopped feed from a camera that remains started behind a system dialog. */
internal class CameraFeedLifecycleObserver(
    private val onForegroundChanged: (Boolean) -> Unit,
    private val onResume: () -> Unit,
    private val onStop: () -> Unit,
) : LifecycleEventObserver {
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_RESUME) {
            onResume()
        }
        if (event == Lifecycle.Event.ON_STOP) {
            onStop()
        }
        onForegroundChanged(source.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
}
