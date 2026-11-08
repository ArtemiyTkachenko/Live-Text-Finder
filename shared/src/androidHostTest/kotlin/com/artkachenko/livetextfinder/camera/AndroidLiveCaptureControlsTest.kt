package com.artkachenko.livetextfinder.camera

import android.graphics.Bitmap
import android.os.Looper
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.FocusMeteringResult
import androidx.camera.core.TorchState
import androidx.camera.core.ZoomState
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import com.artkachenko.livetextfinder.ocr.CapturedCameraFrame
import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.LiveFrameTracker
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.search.FrameIdentity
import com.artkachenko.livetextfinder.search.LiveSearchAreaOwner
import com.artkachenko.livetextfinder.search.NormalizedSearchArea
import com.artkachenko.livetextfinder.search.PreviewTarget
import com.google.common.util.concurrent.ListenableFuture
import java.lang.reflect.Proxy
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidLiveCaptureControlsTest {
    @Test
    fun observedCapabilitiesAfOnlyFocusAndRealTorchState() {
        val fixture = Fixture()
        val adapter = fixture.attach()
        val lease = fixture.controls.state.value.lease
        assertTrue(fixture.controls.state.value.hasZoom)
        assertTrue(fixture.controls.state.value.hasFlash)
        assertTrue(fixture.controls.state.value.hasFocus)
        assertFalse(fixture.controls.state.value.torchOn)
        assertTrue(fixture.focusProbes.all { it.meteringPointsAe.isEmpty() })
        fixture.controls.toggleTorch(lease)
        assertEquals(listOf(true), fixture.torchRequests)
        assertTrue(fixture.controls.state.value.torchPending)
        assertFalse(fixture.controls.state.value.torchOn)
        fixture.torch.value = TorchState.ON
        fixture.torchFuture.complete(null)
        assertTrue(fixture.controls.state.value.torchOn)
        assertFalse(fixture.controls.state.value.torchPending)
        fixture.controls.focusCenter(lease)
        assertTrue(fixture.controls.state.value.focusing)
        assertTrue(fixture.focusActions.single().meteringPointsAe.isEmpty())
        assertEquals(1, fixture.focusActions.single().meteringPointsAf.size)
        fixture.focusFuture.complete(FocusMeteringResult.create(false))
        assertEquals(false, fixture.controls.state.value.focusSucceeded)
        fixture.focusFuture = ControlledFuture()
        fixture.controls.focusCenter(lease)
        fixture.focusFuture.complete(FocusMeteringResult.create(true))
        assertEquals(true, fixture.controls.state.value.focusSucceeded)
        fixture.nativeReady = false
        adapter.refresh()
        fixture.controls.toggleTorch(lease)
        assertEquals(listOf(true), fixture.torchRequests)
        adapter.close()
        assertEquals(listOf(true, false), fixture.torchRequests)
        assertEquals(1, fixture.focusCancels)
        assertFalse(fixture.zoom.hasObservers())
        assertFalse(fixture.torch.hasObservers())
    }

    @Test
    fun unsupportedAfAndFlashAreHiddenUntilMeasuredCapabilitiesAreKnown() {
        val fixture = Fixture()
        fixture.hasFlash = false
        fixture.hasAf = false
        fixture.view.layout(0, 0, 0, 0)
        val adapter = fixture.attach()
        assertFalse(fixture.controls.state.value.hasFocus)
        assertFalse(fixture.controls.state.value.hasFlash)
        fixture.view.layout(0, 0, 320, 240)
        adapter.refresh()
        assertFalse(fixture.controls.state.value.hasFocus)
        fixture.hasAf = true
        adapter.refresh()
        assertTrue(fixture.controls.state.value.hasFocus)
        adapter.close()
    }

    @Test
    fun zoomHardwareFutureGatesResultsCaptureAndGenerationsOnSuccessFailureAndCancel() {
        for (outcome in listOf("success", "failure", "cancel", "synchronous")) {
            val fixture = Fixture()
            val area = LiveSearchAreaOwner()
            area.apply(area.revision, NormalizedSearchArea.CENTERED_DEFAULT)
            val revision = area.revision
            val frames = LiveFrameTracker(20)
            val matrix = listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
            frames.setRunning(true)
            frames.updateTarget(PreviewTarget(320, 240, matrix))
            fun result(timestamp: Long) = OcrUiState(
                OcrStatus.TextRecognized,
                snapshot = OcrSnapshot(
                    "page", emptyList(),
                    FrameInfo(timestamp, 320, 240, 0, TextBounds(0, 0, 320, 240), matrix),
                ),
            )
            assertTrue(frames.receive(frames.ticket(), result(1)))
            val before = frames.ticket()
            var boundaries = 0
            val adapter = fixture.attach { pending ->
                frames.suspendContent(pending)
                boundaries++
            }
            fixture.throwZoom = outcome == "synchronous"
            fixture.controls.zoomIn(fixture.controls.state.value.lease)
            assertNull(frames.state.snapshot)
            assertNull(frames.state.projection)
            assertFalse(frames.receive(before, result(2)))
            if (outcome != "synchronous") {
                assertTrue(fixture.controls.state.value.zoomPending)
                assertFalse(frames.canCapture())
                val during = frames.ticket()
                assertFalse(frames.receive(during, result(3)))
                // ZoomState is optimistic and does not release the hardware gate.
                fixture.zoom.value = zoomState(1.25f)
                assertTrue(fixture.controls.state.value.zoomPending)
                assertFalse(frames.canCapture())
                fixture.commonReady = false
                when (outcome) {
                    "success" -> fixture.zoomFuture.complete(null)
                    "failure" -> fixture.zoomFuture.fail()
                    else -> fixture.zoomFuture.cancel(false)
                }
                assertFalse(frames.receive(during, result(4)))
            }
            assertEquals(2, boundaries)
            assertFalse(fixture.controls.state.value.zoomPending)
            assertEquals(OcrStatus.Scanning, frames.state.status)
            assertTrue(frames.canCapture())
            assertTrue(frames.receive(frames.ticket(), result(5)))
            assertNotNull(frames.state.projection)
            assertEquals(NormalizedSearchArea.CENTERED_DEFAULT, area.area)
            assertEquals(revision, area.revision)
            assertFalse(fixture.zoomFuture.pendingGet)
            adapter.close()
        }
    }

    @Test
    fun rejectionWaitsForCompletionAndOldCompletionCleanupCannotMutateReplacement() {
        val fixture = Fixture()
        var boundaries = 0
        val old = fixture.attach(Executor { throw RejectedExecutionException() }) { boundaries++ }
        val oldLease = fixture.controls.state.value.lease
        fixture.controls.zoomIn(oldLease)
        assertTrue(fixture.controls.state.value.zoomPending)
        assertEquals(1, boundaries)
        fixture.zoomFuture.complete(null)
        assertFalse(fixture.controls.state.value.zoomPending)
        assertEquals(2, boundaries)
        fixture.zoomFuture = ControlledFuture()
        fixture.controls.zoomIn(oldLease)
        val delayed = fixture.zoomFuture
        val replacement = fixture.attach()
        assertFalse(old.isCurrent())
        assertTrue(replacement.isCurrent())
        val lease = fixture.controls.state.value.lease
        val torchCount = fixture.torchRequests.size
        val cancels = fixture.focusCancels
        old.close()
        fixture.controls.zoomIn(oldLease)
        delayed.complete(null)
        assertEquals(lease, fixture.controls.state.value.lease)
        assertEquals(torchCount, fixture.torchRequests.size)
        assertEquals(cancels, fixture.focusCancels)
        assertTrue(fixture.zoom.hasObservers())
        assertFalse(fixture.controls.state.value.zoomPending)
        replacement.close()
    }

    @Test
    fun rejectedCompletionExecutorPostsBackgroundCompletionToMainWithoutReadingPendingFuture() {
        val fixture = Fixture()
        val mainThread = Thread.currentThread()
        val boundaryThreads = mutableListOf<Thread>()
        val adapter = fixture.attach(Executor { throw RejectedExecutionException() }) {
            boundaryThreads += Thread.currentThread()
        }
        fixture.controls.zoomIn(fixture.controls.state.value.lease)
        assertTrue(fixture.controls.state.value.zoomPending)
        assertFalse(fixture.zoomFuture.pendingGet)
        val worker = Thread { fixture.zoomFuture.complete(null) }
        worker.start()
        worker.join(5_000)
        assertFalse(worker.isAlive)
        assertTrue(fixture.controls.state.value.zoomPending)
        assertEquals(listOf(mainThread), boundaryThreads)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(fixture.controls.state.value.zoomPending)
        assertEquals(listOf(mainThread, mainThread), boundaryThreads)
        assertFalse(fixture.zoomFuture.pendingGet)
        adapter.close()
    }

    @Test
    fun freezeDisablesAdjustmentsButKeepsObservedIlluminationUntilOwnedDetach() {
        val fixture = Fixture()
        val frameOwner = FrozenFrameOwner()
        fixture.actionContext = { frameOwner.state.value.mode == FreezeMode.Live }
        fixture.captureContext = { frameOwner.state.value.mode != FreezeMode.Frozen }
        val adapter = fixture.attach()
        val lease = fixture.controls.state.value.lease
        fixture.controls.toggleTorch(lease)
        fixture.torch.value = TorchState.ON
        fixture.torchFuture.complete(null)
        var requestId = 0L
        frameOwner.attachSession(
            requestCapture = {
                requestId = it
                fixture.controls.canCapture()
            },
            cancelCapture = {
            },
        )
        assertTrue(frameOwner.freeze(cameraReady = true))
        assertEquals(FreezeMode.Freezing, frameOwner.state.value.mode)
        assertTrue(fixture.controls.state.value.torchOn)
        fixture.controls.toggleTorch(lease)
        fixture.controls.zoomIn(lease)
        fixture.controls.focusCenter(lease)
        assertEquals(listOf(true), fixture.torchRequests)
        assertTrue(fixture.zoomRequests.isEmpty())
        assertTrue(fixture.focusActions.isEmpty())
        val bitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888)
        val captured = CapturedCameraFrame(
            requestId, bitmap,
            OcrSnapshot(
                "page", emptyList(),
                FrameInfo(9, 8, 4, 0, TextBounds(0, 0, 8, 4), emptyList()),
            ),
            FrameIdentity(1, 1),
        )
        assertTrue(frameOwner.accept(captured))
        assertFalse(fixture.controls.canCapture())
        assertEquals(listOf(true), fixture.torchRequests)
        adapter.close()
        assertEquals(listOf(true, false), fixture.torchRequests)
        frameOwner.stop()
        bitmap.recycle()
    }

    @Test
    fun nativeBoundsInvalidInputsFutureFailuresAndCleanupAreBounded() {
        val fixture = Fixture()
        val adapter = fixture.attach()
        adapter.zoom(Float.NaN)
        adapter.zoom(Float.POSITIVE_INFINITY)
        assertTrue(fixture.zoomRequests.isEmpty())
        adapter.zoom(100f)
        assertEquals(listOf(4f), fixture.zoomRequests)
        fixture.zoomFuture.complete(null)
        val lease = fixture.controls.state.value.lease
        for (cancel in listOf(false, true)) {
            fixture.torchFuture = ControlledFuture()
            fixture.controls.toggleTorch(lease)
            if (cancel) fixture.torchFuture.cancel(false) else fixture.torchFuture.fail()
            assertFalse(fixture.controls.state.value.torchPending)
            assertFalse(fixture.controls.state.value.torchOn)
            fixture.focusFuture = ControlledFuture()
            fixture.controls.focusCenter(lease)
            if (cancel) fixture.focusFuture.cancel(false) else fixture.focusFuture.fail()
            assertFalse(fixture.controls.state.value.focusing)
            assertEquals(false, fixture.controls.state.value.focusSucceeded)
        }
        fixture.throwCleanup = true
        adapter.close()
        assertFalse(fixture.zoom.hasObservers())
        assertFalse(fixture.torch.hasObservers())
        assertEquals(0L, fixture.controls.state.value.lease)
        assertEquals(1, fixture.focusCancels)
    }

    private class Fixture {
        var commonReady = true
        var nativeReady = true
        var hasFlash = true
        var hasAf = true
        var throwZoom = false
        var throwCleanup = false
        var actionContext: () -> Boolean = { commonReady }
        var captureContext: () -> Boolean = { commonReady }
        val controls = LiveCaptureControls({ actionContext() }, { captureContext() })
        val zoom = MutableLiveData(zoomState(1f))
        val torch = MutableLiveData(TorchState.OFF)
        val view = PreviewView(RuntimeEnvironment.getApplication()).apply {
            layout(0, 0, 320, 240)
        }
        val lifecycleOwner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this).apply {
                handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                handleLifecycleEvent(Lifecycle.Event.ON_START)
            }
        }
        var zoomFuture = ControlledFuture<Void>()
        var torchFuture = ControlledFuture<Void>()
        var focusFuture = ControlledFuture<FocusMeteringResult>()
        val zoomRequests = mutableListOf<Float>()
        val torchRequests = mutableListOf<Boolean>()
        val focusProbes = mutableListOf<FocusMeteringAction>()
        val focusActions = mutableListOf<FocusMeteringAction>()
        var focusCancels = 0
        private val info = proxy<CameraInfo> { name, arguments ->
            when (name) {
                "getZoomState" -> zoom
                "getTorchState" -> torch
                "hasFlashUnit" -> hasFlash
                "isFocusMeteringSupported" -> {
                    val action = arguments[0] as FocusMeteringAction
                    focusProbes += action
                    hasAf && action.meteringPointsAf.isNotEmpty() &&
                        action.meteringPointsAe.isEmpty()
                }
                else -> error("Unexpected CameraInfo call: $name")
            }
        }
        private val control = proxy<CameraControl> { name, arguments ->
            when (name) {
                "setZoomRatio" -> {
                    if (throwZoom) {
                        throw IllegalStateException("Camera closed")
                    }
                    zoomRequests += arguments[0] as Float
                    zoomFuture
                }
                "enableTorch" -> {
                    val enabled = arguments[0] as Boolean
                    torchRequests += enabled
                    if (throwCleanup && !enabled) {
                        throw IllegalStateException("Camera closed")
                    }
                    torchFuture
                }
                "startFocusAndMetering" -> {
                    focusActions += arguments[0] as FocusMeteringAction
                    focusFuture
                }
                "cancelFocusAndMetering" -> {
                    focusCancels++
                    if (throwCleanup) {
                        throw IllegalStateException("Camera closed")
                    }
                    ControlledFuture<Void>().apply { complete(null) }
                }
                else -> error("Unexpected CameraControl call: $name")
            }
        }

        fun attach(
            executor: Executor = Executor { it.run() },
            onBoundary: (Boolean) -> Unit = {
            },
        ) = AndroidLiveCaptureControls(
            controls, info, control, view, lifecycleOwner,
            { nativeReady }, onBoundary, executor,
        )
    }

    private class ControlledFuture<T> : ListenableFuture<T> {
        private val listeners = mutableListOf<Pair<Runnable, Executor>>()
        private var done = false
        private var cancelled = false
        private var failure = false
        private var value: T? = null
        var pendingGet = false
            private set

        fun complete(value: T?) {
            this.value = value
            finish()
        }

        fun fail() {
            failure = true
            finish()
        }

        private fun finish() {
            done = true
            listeners.toList().forEach { (listener, executor) -> executor.execute(listener) }
            listeners.clear()
        }

        override fun addListener(listener: Runnable, executor: Executor) {
            if (done) executor.execute(listener) else listeners += listener to executor
        }

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            cancelled = true
            finish()
            return true
        }

        override fun isCancelled(): Boolean = cancelled

        override fun isDone(): Boolean = done

        override fun get(): T {
            if (!done) {
                pendingGet = true
                error("Pending future read")
            }
            if (cancelled) {
                throw CancellationException()
            }
            if (failure) {
                throw ExecutionException(IllegalStateException())
            }
            @Suppress("UNCHECKED_CAST")
            return value as T
        }

        override fun get(timeout: Long, unit: TimeUnit): T = get()
    }

    companion object {
        private fun zoomState(ratio: Float): ZoomState = proxy { name, _ ->
            when (name) {
                "getZoomRatio" -> ratio
                "getMinZoomRatio" -> 1f
                "getMaxZoomRatio" -> 4f
                "getLinearZoom" -> 0f
                else -> error("Unexpected ZoomState call: $name")
            }
        }

        private inline fun <reified T> proxy(
            crossinline invoke: (String, Array<out Any?>) -> Any?,
        ): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) {
            _, method, arguments -> invoke(method.name, arguments ?: emptyArray())
        } as T
    }
}
