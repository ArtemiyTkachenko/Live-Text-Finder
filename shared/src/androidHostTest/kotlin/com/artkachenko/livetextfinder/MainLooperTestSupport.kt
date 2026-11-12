package com.artkachenko.livetextfinder

import android.os.Looper
import org.robolectric.Shadows.shadowOf

/**
 * Runs everything already posted to the main looper. Call it from `@After` in any host test
 * that writes Compose snapshot state without composing.
 *
 * Once a Compose test has run in the JVM, a snapshot write on the main thread makes Compose
 * post a global apply to its process-wide UI dispatcher. Robolectric discards messages still
 * queued when a test ends, which leaves that dispatcher marked as scheduled with nothing
 * queued, so no later test in the same JVM can recompose.
 */
internal fun drainMainLooper() {
    shadowOf(Looper.getMainLooper()).idle()
}
