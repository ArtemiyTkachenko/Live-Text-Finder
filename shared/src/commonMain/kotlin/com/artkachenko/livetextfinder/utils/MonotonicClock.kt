package com.artkachenko.livetextfinder.utils

import kotlin.time.TimeSource

/** One process-wide monotonic origin shared by OCR receipt and UI grace deadlines. */
internal object MonotonicClock {
    private val origin = TimeSource.Monotonic.markNow()

    fun nowMillis(): Long = origin.elapsedNow().inWholeMilliseconds
}
