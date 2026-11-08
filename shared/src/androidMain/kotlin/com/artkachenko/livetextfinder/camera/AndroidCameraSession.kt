package com.artkachenko.livetextfinder.camera

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.artkachenko.livetextfinder.ocr.CameraTimingDiagnostics
import com.artkachenko.livetextfinder.ocr.CapturedCameraFrame
import com.artkachenko.livetextfinder.ocr.LiveFrameTracker
import com.artkachenko.livetextfinder.ocr.MlKitTextAnalyzer
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.search.PreviewTarget

/** Owns only this preview/analysis pair. All methods and listeners are main-thread confined. */
internal class AndroidCameraSession(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val view: PreviewView,
    private val controls: LiveCaptureControls,
    private val isControlsContextCurrent: () -> Boolean,
    private val onCaptured: (CapturedCameraFrame) -> Boolean = { false },
    private val onCaptureFailure: (Long, String) -> Unit = { _, _ -> },
    private val onState: (OcrUiState) -> Unit,
) : AutoCloseable {
    private val executor = ContextCompat.getMainExecutor(context)
    private val preview = Preview.Builder().build()
    private val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
    private var analyzer: MlKitTextAnalyzer? = null
    private var provider: ProcessCameraProvider? = null
    private var boundCamera: Camera? = null
    private var captureControls: AndroidLiveCaptureControls? = null
    private var cameraState: androidx.lifecycle.LiveData<CameraState>? = null
    private var closed = false
    private val handler = Handler(Looper.getMainLooper())
    private val frames = LiveFrameTracker(++nextSessionId)
    private val diagnostics = if (
        context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    ) {
        CameraTimingDiagnostics()
    } else {
        null
    }
    private var emittedState: OcrUiState? = null
    private var targetRotation: Int? = null
    private val expiry = Runnable {
        frames.expire()
        publish()
        scheduleExpiry()
    }
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshMapping() }
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener { refreshMapping(); true }
    private val streamObserver = Observer<PreviewView.StreamState> { refreshMapping() }
    private val lifecycleObserver = CameraFeedLifecycleObserver(
        onForegroundChanged = { refreshMapping() },
        onResume = {
        },
        onStop = ::close,
    )
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (view.display?.displayId == displayId) updateRotation()
        }
    }
    private val stateObserver = Observer<CameraState> { state ->
        if (!closed && ownsControlLease() && state.error != null) {
            fail(OcrUiState(OcrStatus.Error, "Camera is unavailable (code ${state.error?.code}). Retry the camera."))
        }
    }

    fun start() {
        try {
            view.addOnLayoutChangeListener(layoutListener)
            view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            view.previewStreamState.observe(owner, streamObserver)
            owner.lifecycle.addObserver(lifecycleObserver)
            analyzer = MlKitTextAnalyzer(
                executor = executor,
                captureIdentity = {
                    refreshMapping()
                    frames.ticket()
                },
                captureExecutor = CaptureCopyWorker,
                diagnostics = diagnostics,
                onCaptured = { captured ->
                    !closed && captureControls?.isCurrent() == true &&
                        controls.canCapture() && controlsContextCurrent() &&
                        frames.ticket() == captured.identity && onCaptured(captured)
                },
                onCaptureFailure = { requestId, message ->
                    if (!closed && ownsControlLease()) {
                        onCaptureFailure(requestId, message)
                    }
                },
                onState = { state, identity ->
                    if (!closed && ownsControlLease()) {
                        refreshMapping()
                        val accepted = frames.receive(identity, state)
                        val frame = state.snapshot?.frame
                        if (accepted) {
                            diagnostics?.frameAccepted(identity, frame)
                        } else {
                            val current = frames.ticket()
                            diagnostics?.frameRejected(identity, current, frame)
                        }
                        if (accepted) {
                            publish()
                            scheduleExpiry()
                        }
                    }
                },
            )
            analysis.setAnalyzer(executor, checkNotNull(analyzer))
            preview.surfaceProvider = view.surfaceProvider
            displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!closed) {
                    try {
                        val cameraProvider = future.get()
                        provider = cameraProvider
                        if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                            fail(OcrUiState(OcrStatus.Unavailable, "No rear camera is available on this device."))
                        } else {
                            updateRotation()
                            // Release the old hardware lease before binding its replacement.
                            controls.detach(controls.state.value.lease)
                            val camera = cameraProvider.bindToLifecycle(
                                owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                            )
                            boundCamera = camera
                            captureControls = AndroidLiveCaptureControls(
                                controls = controls,
                                cameraInfo = camera.cameraInfo,
                                cameraControl = camera.cameraControl,
                                view = view,
                                lifecycleOwner = owner,
                                isNativeContextCurrent = ::controlsContextCurrent,
                                onZoomBoundary = { pending ->
                                    frames.suspendContent(pending)
                                    publish()
                                    scheduleExpiry()
                                },
                                mainExecutor = executor,
                            )
                            cameraState = camera.cameraInfo.cameraState
                            cameraState?.observe(owner, stateObserver)
                        }
                    } catch (error: Exception) {
                        fail(OcrUiState(OcrStatus.Error, "Could not start the rear camera. Check access and retry."))
                    }
                }
            }, executor)
        } catch (error: Exception) {
            fail(OcrUiState(OcrStatus.Error, "Could not initialize camera recognition. Retry the camera."))
        }
    }

    fun requestCapture(requestId: Long): Boolean {
        if (closed || captureControls?.isCurrent() != true ||
            controls.state.value.zoomPending
        ) {
            return false
        }
        refreshMapping()
        if (captureControls?.isCurrent() != true || !controlsContextCurrent() ||
            !controls.canCapture()
        ) {
            return false
        }
        if (!frames.canCapture()) {
            return false
        }
        return analyzer?.requestCapture(requestId) == true
    }

    fun cancelCapture(requestId: Long) {
        analyzer?.cancelCapture(requestId)
    }

    private fun fail(state: OcrUiState) {
        if (closed) return
        val shouldReport = ownsControlLease()
        close()
        if (shouldReport) {
            onState(state)
        }
    }

    private fun updateRotation() {
        if (closed || !ownsControlLease()) return
        view.display?.rotation?.let { rotation ->
            if (targetRotation != rotation) {
                frames.invalidateGeometry()
                targetRotation = rotation
                preview.targetRotation = rotation
                analysis.targetRotation = rotation
                publish()
            }
        }
    }

    private fun ownsControlLease(): Boolean = captureControls?.isCurrent() != false

    private fun controlsContextCurrent(): Boolean =
        !closed && boundCamera != null && isControlsContextCurrent() &&
            owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
            view.previewStreamState.value == PreviewView.StreamState.STREAMING &&
            view.width > 0 && view.height > 0 && view.isAttachedToWindow

    /** All capture/listener paths are main-thread confined; no ImageProxy is retained here. */
    private fun refreshMapping() {
        if (closed || !ownsControlLease()) return
        updateRotation()
        val active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
            view.previewStreamState.value == PreviewView.StreamState.STREAMING &&
            view.width > 0 && view.height > 0 && view.isAttachedToWindow
        frames.setRunning(active)
        val matrix = if (active) view.sensorToViewTransform else null
        val target = matrix?.let {
            val values = FloatArray(9)
            it.getValues(values)
            PreviewTarget(view.width, view.height, values.toList())
        }
        frames.updateTarget(target)
        captureControls?.refresh()
        // Invalidate stale results even if pre-draw arrives before a delayed expiry callback.
        frames.expire()
        publish()
        scheduleExpiry()
    }

    private fun publish() {
        if (!closed && ownsControlLease() && emittedState != frames.state) {
            emittedState = frames.state
            onState(frames.state)
        }
    }

    private fun scheduleExpiry() {
        handler.removeCallbacks(expiry)
        if (!closed && ownsControlLease()) {
            frames.millisUntilExpiry()?.let { handler.postDelayed(expiry, it) }
        }
    }

    override fun close() {
        if (closed) return
        val shouldReport = ownsControlLease()
        closed = true
        captureControls?.close()
        captureControls = null
        boundCamera = null
        frames.dispose()
        handler.removeCallbacks(expiry)
        view.removeOnLayoutChangeListener(layoutListener)
        if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        view.previewStreamState.removeObserver(streamObserver)
        owner.lifecycle.removeObserver(lifecycleObserver)
        if (shouldReport) {
            onState(frames.state)
        }
        displayManager.unregisterDisplayListener(displayListener)
        cameraState?.removeObserver(stateObserver)
        analysis.clearAnalyzer()
        provider?.unbind(preview, analysis)
        preview.surfaceProvider = null
        // Defers recognizer disposal while an asynchronous frame still uses it.
        analyzer?.close()
    }

    companion object { private var nextSessionId = 0L }
}
