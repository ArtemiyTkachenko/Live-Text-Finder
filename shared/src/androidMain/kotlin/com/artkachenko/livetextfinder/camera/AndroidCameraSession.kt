package com.artkachenko.livetextfinder.camera

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
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
import com.artkachenko.livetextfinder.ocr.MlKitTextAnalyzer
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState

/** Owns only this preview/analysis pair. All methods and listeners are main-thread confined. */
internal class AndroidCameraSession(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val view: PreviewView,
    private val onState: (OcrUiState) -> Unit,
) : AutoCloseable {
    private val executor = ContextCompat.getMainExecutor(context)
    private val preview = Preview.Builder().build()
    private val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
    private var analyzer: MlKitTextAnalyzer? = null
    private var provider: ProcessCameraProvider? = null
    private var cameraState: androidx.lifecycle.LiveData<CameraState>? = null
    private var closed = false
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (view.display?.displayId == displayId) updateRotation()
        }
    }
    private val stateObserver = Observer<CameraState> { state ->
        if (!closed && state.error != null) {
            fail(OcrUiState(OcrStatus.Error, "Camera is unavailable (code ${state.error?.code}). Retry the camera."))
        }
    }

    fun start() {
        try {
            analyzer = MlKitTextAnalyzer(executor) {
                if (!closed && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    onState(it)
                }
            }
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
                            val camera = cameraProvider.bindToLifecycle(
                                owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
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

    private fun fail(state: OcrUiState) {
        if (closed) return
        close()
        onState(state)
    }

    private fun updateRotation() {
        view.display?.rotation?.let {
            preview.targetRotation = it
            analysis.targetRotation = it
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        displayManager.unregisterDisplayListener(displayListener)
        cameraState?.removeObserver(stateObserver)
        analysis.clearAnalyzer()
        provider?.unbind(preview, analysis)
        preview.surfaceProvider = null
        // Defers recognizer disposal while an asynchronous frame still uses it.
        analyzer?.close()
    }
}
