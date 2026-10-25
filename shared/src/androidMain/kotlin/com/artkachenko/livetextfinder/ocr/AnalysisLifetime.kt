package com.artkachenko.livetextfinder.ocr

/** Main-thread confined. Holds the detector alive until its last async request finishes. */
internal class AnalysisLifetime(private val release: () -> Unit) {
    private var busy = false
    private var closed = false
    private var released = false

    fun tryBegin(): Boolean {
        if (closed || busy) return false
        busy = true
        return true
    }

    fun finish() {
        check(busy)
        busy = false
        releaseIfIdle()
    }

    fun close() {
        closed = true
        releaseIfIdle()
    }

    private fun releaseIfIdle() {
        if (closed && !busy && !released) {
            released = true
            release()
        }
    }
}
