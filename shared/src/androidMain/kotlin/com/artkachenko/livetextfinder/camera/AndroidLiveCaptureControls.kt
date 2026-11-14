package com.artkachenko.livetextfinder.camera

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.TorchState
import androidx.camera.core.ZoomState
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** Narrow main-thread adapter for the CameraX camera currently bound by the session. */
internal class AndroidLiveCaptureControls(
    private val controls: LiveCaptureControls,
    private val cameraInfo: CameraInfo,
    private val cameraControl: CameraControl,
    private val view: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val isNativeContextCurrent: () -> Boolean,
    private val onZoomBoundary: (Boolean) -> Unit,
    private val mainExecutor: Executor,
) : AutoCloseable, LiveCaptureActions {
    private val handler = Handler(Looper.getMainLooper())
    private var detached = false
    private val lease = controls.attach(this, ::detachNative)
    private val zoomObserver = Observer<ZoomState> { updateObservedState() }
    private val torchObserver = Observer<Int> { updateObservedState() }

    private val pinchDetector = ScaleGestureDetector(
        view.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean =
                isNativeContextCurrent() && controls.beginPinch(lease)

            override fun onScale(detector: ScaleGestureDetector): Boolean =
                isNativeContextCurrent() && controls.scalePinch(lease, detector.scaleFactor)

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                controls.endPinch(lease)
            }
        },
    ).apply {
        isQuickScaleEnabled = false
        isStylusScaleEnabled = false
    }
    private var trackingTouch = false

    init {
        view.setOnTouchListener { _, event -> onPreviewTouch(event) }
        cameraInfo.zoomState.observe(lifecycleOwner, zoomObserver)
        cameraInfo.torchState.observe(lifecycleOwner, torchObserver)
        refresh()
    }

    private fun onPreviewTouch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            trackingTouch = isNativeContextCurrent() && controls.canPinch(lease)
        }
        if (!trackingTouch) return false
        if (event.actionMasked == MotionEvent.ACTION_CANCEL ||
            !isNativeContextCurrent() || !controls.canPinch(lease)
        ) {
            controls.cancelPinch(lease)
            trackingTouch = false
        }
        pinchDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) trackingTouch = false
        return true
    }

    fun refresh() {
        if (!ownsCamera()) return
        updateObservedState()
        val focusAction = centerFocusAction()
        val supported = focusAction != null && runCatching {
            cameraInfo.isFocusMeteringSupported(focusAction)
        }.getOrDefault(false)
        controls.update(lease) {
            it.copy(ready = isNativeContextCurrent(), hasFocus = supported)
        }
    }

    private fun updateObservedState() {
        if (!ownsCamera()) return
        val zoom = cameraInfo.zoomState.value
        controls.update(lease) {
            it.copy(
                minZoom = zoom?.minZoomRatio ?: 1f,
                maxZoom = zoom?.maxZoomRatio ?: 1f,
                zoom = zoom?.zoomRatio ?: 1f,
                hasFlash = cameraInfo.hasFlashUnit(),
                torchOn = cameraInfo.torchState.value == TorchState.ON,
            )
        }
    }

    fun isCurrent(): Boolean = ownsCamera()

    private fun ownsCamera(): Boolean = !detached && controls.isCurrent(lease)

    private fun canDispatch(): Boolean =
        ownsCamera() && controls.canDispatch(lease) && isNativeContextCurrent()

    override fun zoom(ratio: Float) {
        val current = controls.state.value
        if (!canDispatch() || !current.hasZoom || current.zoomPending || !ratio.isFinite()) return
        val target = ratio.coerceIn(current.minZoom, current.maxZoom)
        if (target == current.zoom) return
        controls.update(lease) { it.copy(zoomPending = true) }
        onZoomBoundary(true)
        submit(
            command = { cameraControl.setZoomRatio(target) },
            completed = {
                // Drain the current lease even if area editing or Freezing now disables actions.
                onZoomBoundary(false)
                controls.update(lease) { it.copy(zoomPending = false) }
                updateObservedState()
                controls.dispatchQueuedPinch(lease)
            },
        )
    }

    override fun torch(enabled: Boolean) {
        val current = controls.state.value
        if (!canDispatch() || !current.hasFlash || current.torchPending) return
        controls.update(lease) { it.copy(torchPending = true) }
        submit(
            command = { cameraControl.enableTorch(enabled) },
            completed = { controls.update(lease) { it.copy(torchPending = false) } },
        )
    }

    override fun focus() {
        if (!canDispatch() || controls.state.value.focusing) return
        val action = centerFocusAction() ?: return
        val supported = runCatching {
            cameraInfo.isFocusMeteringSupported(action)
        }.getOrDefault(false)
        if (!supported) return
        controls.update(lease) { it.copy(focusing = true, focusSucceeded = null) }
        submit(
            command = { cameraControl.startFocusAndMetering(action) },
            completed = { result ->
                controls.update(lease) {
                    it.copy(focusing = false, focusSucceeded = result?.isFocusSuccessful == true)
                }
            },
        )
    }

    private fun centerFocusAction(): FocusMeteringAction? {
        if (view.width <= 0 || view.height <= 0) return null
        val point = view.meteringPointFactory.createPoint(view.width / 2f, view.height / 2f)
        return FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(2, TimeUnit.SECONDS)
            .build()
    }

    private fun <T> submit(command: () -> ListenableFuture<T>, completed: (T?) -> Unit) {
        val future = try {
            command()
        } catch (error: Exception) {
            if (ownsCamera()) {
                completed(null)
            }
            return
        }
        var delivered = false
        val listener = Runnable {
            if (!delivered && ownsCamera()) {
                delivered = true
                // CameraX invokes this only on completion; never read a pending future on main.
                val result = runCatching { future.get() }.getOrNull()
                completed(result)
            }
        }
        val safeMain = Executor { task ->
            try {
                mainExecutor.execute(task)
            } catch (error: RuntimeException) {
                postToMain(task)
            }
        }
        try {
            future.addListener(listener, safeMain)
        } catch (error: RuntimeException) {
            // A bounded fallback preserves pending admission until actual future completion.
            runCatching { future.addListener(listener, Executor(::postToMain)) }
                .onFailure {
                    if (future.isDone) {
                        postToMain(listener)
                    }
                }
        }
    }

    private fun postToMain(task: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run()
        } else {
            handler.post(task)
        }
    }

    private fun detachNative() {
        if (detached) return
        // Ownership, not action readiness: normal close may already mark the session closed.
        val wasCurrent = controls.isCurrent(lease)
        detached = true
        cameraInfo.zoomState.removeObserver(zoomObserver)
        cameraInfo.torchState.removeObserver(torchObserver)
        if (wasCurrent) {
            view.setOnTouchListener(null)
            runCatching { cameraControl.enableTorch(false) }
            runCatching { cameraControl.cancelFocusAndMetering() }
        }
    }

    override fun close() {
        if (controls.isCurrent(lease)) {
            controls.detach(lease)
        } else {
            detachNative()
        }
    }
}
