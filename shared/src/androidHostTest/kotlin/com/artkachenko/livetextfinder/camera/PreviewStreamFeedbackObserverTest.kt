package com.artkachenko.livetextfinder.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreviewStreamFeedbackObserverTest {
    @Test
    fun cameraStartPublishesStartingBeforeSessionStartAndOldRetryCannotAdvanceReplacement() {
        val startOrder = mutableListOf<String>()
        var publishedState = OcrUiState(OcrStatus.Paused)
        startCameraAttempt(
            report = { state ->
                publishedState = state
                startOrder += "published"
            },
            startSession = {
                assertEquals(OcrStatus.Starting, publishedState.status)
                startOrder += "session-start"
            },
        )
        assertEquals(listOf("published", "session-start"), startOrder)

        var attempt = 6
        val retryFromAttemptA = cameraAttemptRetryCallback(
            expectedAttempt = 5,
            currentAttempt = { attempt },
            updateAttempt = { attempt = it },
        )
        retryFromAttemptA()
        assertEquals(6, attempt)

        val retryFromAttemptB = cameraAttemptRetryCallback(
            expectedAttempt = attempt,
            currentAttempt = { attempt },
            updateAttempt = { attempt = it },
        )
        retryFromAttemptB()
        assertEquals(7, attempt)
        retryFromAttemptA()
        assertEquals(7, attempt)
    }

    @Test
    fun lifecycleGatesFeedbackReplaysCurrentStateAndDestroyDetachesObserver() {
        val owner = TestLifecycleOwner()
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        val stream = MutableLiveData(PreviewView.StreamState.IDLE)
        val feedback = mutableListOf<PreviewStreamFeedback>()
        val observer = PreviewStreamFeedbackObserver(feedback::add)
        observer.observe(stream, owner)

        assertEquals(listOf(PreviewStreamFeedback.Starting), feedback)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        stream.value = PreviewView.StreamState.STREAMING
        assertEquals(
            listOf(PreviewStreamFeedback.Starting, PreviewStreamFeedback.Streaming),
            feedback,
        )

        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        val feedbackBeforeStop = feedback.toList()
        stream.value = PreviewView.StreamState.IDLE
        stream.value = PreviewView.StreamState.STREAMING
        assertEquals(feedbackBeforeStop, feedback)

        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        assertEquals(feedbackBeforeStop + PreviewStreamFeedback.Streaming, feedback)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        stream.value = PreviewView.StreamState.IDLE
        assertEquals(PreviewStreamFeedback.Paused, feedback.last())

        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        assertFalse(stream.hasObservers())
        val feedbackBeforeDestroyEmission = feedback.toList()
        stream.value = PreviewView.StreamState.IDLE
        assertEquals(feedbackBeforeDestroyEmission, feedback)
        observer.close()
        observer.close()
        assertFalse(stream.hasObservers())
    }

    @Test
    fun followsPreviewStreamAndDetachesFromReplacedPreview() {
        val owner = TestLifecycleOwner()
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        val originalStream = MutableLiveData(PreviewView.StreamState.IDLE)
        val feedback = mutableListOf<PreviewStreamFeedback>()
        val originalObserver = PreviewStreamFeedbackObserver(feedback::add)
        originalObserver.observe(originalStream, owner)

        assertEquals(listOf(PreviewStreamFeedback.Starting), feedback)
        assertTrue(shouldShowStreamFeedback(feedback.last(), failed = false))

        val ocrState = OcrUiState(OcrStatus.Starting)
        originalStream.value = PreviewView.StreamState.STREAMING
        assertEquals(OcrStatus.Starting, ocrState.status)
        assertEquals(PreviewStreamFeedback.Streaming, feedback.last())
        assertFalse(shouldShowStreamFeedback(feedback.last(), failed = false))

        originalStream.value = PreviewView.StreamState.IDLE
        assertEquals(PreviewStreamFeedback.Paused, feedback.last())
        assertTrue(shouldShowStreamFeedback(feedback.last(), failed = false))

        originalObserver.close()
        val replacementStream = MutableLiveData(PreviewView.StreamState.IDLE)
        val replacementFeedback = mutableListOf<PreviewStreamFeedback>()
        val replacementObserver = PreviewStreamFeedbackObserver(replacementFeedback::add)
        replacementObserver.observe(replacementStream, owner)
        assertFalse(originalStream.hasObservers())

        originalStream.value = PreviewView.StreamState.STREAMING
        assertEquals(listOf(PreviewStreamFeedback.Starting), replacementFeedback)
        assertFalse(shouldShowStreamFeedback(PreviewStreamFeedback.Paused, failed = true))
        assertEquals(48, CAMERA_RETRY_MIN_HEIGHT_DP)

        replacementObserver.close()
        assertFalse(replacementStream.hasObservers())
    }

    private class TestLifecycleOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }
}
