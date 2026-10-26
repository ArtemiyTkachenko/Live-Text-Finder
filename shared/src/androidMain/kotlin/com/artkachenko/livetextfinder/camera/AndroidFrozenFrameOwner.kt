package com.artkachenko.livetextfinder.camera

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.artkachenko.livetextfinder.ocr.CapturedCameraFrame

@Composable
internal actual fun rememberFrozenFrameOwner(): FrozenFrameOwner {
    val lifecycleOwner = LocalLifecycleOwner.current
    val owner = remember { FrozenFrameOwner() }
    DisposableEffect(owner, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                owner.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            owner.stop()
        }
    }
    return owner
}

internal actual class FrozenFrameOwner actual constructor() {
    private val mutableState = mutableStateOf(FreezePresentationState())
    private val handler = Handler(Looper.getMainLooper())
    private val controller = FrozenFrameController(
        publish = { state -> mutableState.value = state },
    )
    private var nextRequestId = 0L
    private var bitmap: Bitmap? = null
    private var captureRequest: ((Long) -> Boolean)? = null
    private var captureCancel: ((Long) -> Unit)? = null
    private val timeout = Runnable {
        val requestId = mutableState.value.requestId ?: return@Runnable
        if (controller.expire(SystemClock.elapsedRealtime()) == null) {
            captureCancel?.invoke(requestId)
        }
    }

    internal actual val state: State<FreezePresentationState> = mutableState

    internal actual fun freeze(cameraReady: Boolean): Boolean {
        val request = captureRequest ?: return false
        val requestId = ++nextRequestId
        if (!controller.begin(requestId, SystemClock.elapsedRealtime(), cameraReady)) {
            return false
        }
        handler.postDelayed(timeout, CAPTURE_TIMEOUT_MILLIS)
        if (!request(requestId)) {
            handler.removeCallbacks(timeout)
            controller.fail(
                requestId,
                "Camera capture is not ready. Retry when the preview is active.",
            )
        }
        return true
    }

    internal actual fun cancel() {
        val requestId = mutableState.value.requestId ?: return
        captureCancel?.invoke(requestId)
        handler.removeCallbacks(timeout)
        controller.cancel(requestId)
    }

    internal actual fun resume() {
        handler.removeCallbacks(timeout)
        if (controller.resume()) {
            bitmap = null
        }
    }

    internal actual fun stop() {
        val requestId = mutableState.value.requestId
        if (requestId != null) {
            captureCancel?.invoke(requestId)
        }
        handler.removeCallbacks(timeout)
        controller.stop()
        bitmap = null
    }

    internal fun attachSession(
        requestCapture: (Long) -> Boolean,
        cancelCapture: (Long) -> Unit,
    ) {
        captureRequest = requestCapture
        captureCancel = cancelCapture
    }

    internal fun detachSession() {
        val requestId = mutableState.value.requestId
        if (mutableState.value.mode == FreezeMode.Freezing && requestId != null) {
            captureCancel?.invoke(requestId)
            handler.removeCallbacks(timeout)
            controller.fail(requestId, "Freeze canceled because the camera preview closed.")
        }
        captureRequest = null
        captureCancel = null
    }

    internal fun accept(captured: CapturedCameraFrame): Boolean {
        if (mutableState.value.requestId != captured.requestId) {
            return false
        }
        val frame = captured.snapshot.frame
        if (captured.bitmap.width != frame.bufferWidth ||
            captured.bitmap.height != frame.bufferHeight ||
            frame.rotationDegrees !in VALID_ROTATIONS
        ) {
            fail(captured.requestId, "The copied camera pixels do not match the OCR frame.")
            return false
        }
        bitmap = captured.bitmap
        val accepted = controller.accept(captured.asSearchFrame(), SystemClock.elapsedRealtime())
        if (accepted) {
            handler.removeCallbacks(timeout)
        } else {
            bitmap = null
            captureCancel?.invoke(captured.requestId)
        }
        return accepted
    }

    internal fun fail(requestId: Long, message: String) {
        if (controller.fail(requestId, message)) {
            handler.removeCallbacks(timeout)
        }
    }

    internal fun currentBitmap(): Bitmap? = bitmap

    private fun CapturedCameraFrame.asSearchFrame() = FrozenSearchFrame(
        requestId = requestId,
        identity = identity,
        snapshot = snapshot,
    )

    private companion object {
        const val CAPTURE_TIMEOUT_MILLIS = 5_000L
        val VALID_ROTATIONS = setOf(0, 90, 180, 270)
    }
}

@Composable
internal fun FrozenBitmapPreview(
    owner: FrozenFrameOwner,
    modifier: Modifier = Modifier,
    isVisible: () -> Boolean,
) {
    val state = owner.state.value
    val bitmap = owner.currentBitmap()
    val frame = state.frame
    if (state.mode != FreezeMode.Frozen || bitmap == null || frame == null) {
        return
    }
    Canvas(modifier) {
        drawFrozenBitmap(owner, frame, bitmap, isVisible)
    }
}
