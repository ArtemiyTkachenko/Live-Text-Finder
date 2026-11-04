package com.artkachenko.livetextfinder.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

internal enum class PreviewStreamFeedback {
    Starting,
    Streaming,
    Paused,
}

internal fun shouldShowStreamFeedback(
    feedback: PreviewStreamFeedback,
    failed: Boolean,
): Boolean = !failed && feedback != PreviewStreamFeedback.Streaming

internal const val CAMERA_RETRY_MIN_HEIGHT_DP = 48

/** Observes one preview's stream and distinguishes its initial wait from an interruption. */
internal class PreviewStreamFeedbackObserver(
    private val onFeedback: (PreviewStreamFeedback) -> Unit,
) : AutoCloseable {
    private var source: LiveData<PreviewView.StreamState>? = null
    private var observer: Observer<PreviewView.StreamState>? = null
    private var hasStreamed = false
    private var closed = false

    fun observe(
        streamState: LiveData<PreviewView.StreamState>,
        owner: LifecycleOwner,
    ) {
        check(source == null)
        source = streamState
        val streamObserver = Observer<PreviewView.StreamState> { state ->
            if (!closed) {
                if (state == PreviewView.StreamState.STREAMING) {
                    hasStreamed = true
                    onFeedback(PreviewStreamFeedback.Streaming)
                } else {
                    onFeedback(
                        if (hasStreamed) PreviewStreamFeedback.Paused
                        else PreviewStreamFeedback.Starting,
                    )
                }
            }
        }
        observer = streamObserver
        streamState.observe(owner, streamObserver)
    }

    override fun close() {
        if (closed) return
        closed = true
        val streamObserver = observer
        if (streamObserver != null) {
            source?.removeObserver(streamObserver)
        }
        observer = null
        source = null
    }
}
