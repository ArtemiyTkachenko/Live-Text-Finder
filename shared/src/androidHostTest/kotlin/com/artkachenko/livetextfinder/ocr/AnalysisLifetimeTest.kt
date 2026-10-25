package com.artkachenko.livetextfinder.ocr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalysisLifetimeTest {
    @Test fun rejectsOverlapUntilResultCallbackEvenIfCameraBufferHasClosed() {
        val lifetime = AnalysisLifetime {}
        assertTrue(lifetime.tryBegin())
        assertFalse(lifetime.tryBegin())
        lifetime.finish()
        assertTrue(lifetime.tryBegin())
        lifetime.finish()
    }

    @Test fun disposalWaitsForRecognitionAndRejectsNewFrames() {
        var releases = 0
        val lifetime = AnalysisLifetime { releases++ }
        assertTrue(lifetime.tryBegin())
        lifetime.close()
        lifetime.close()
        assertEquals(0, releases)
        assertFalse(lifetime.tryBegin())
        lifetime.finish()
        assertEquals(1, releases)
        lifetime.close()
        assertEquals(1, releases)
        assertFalse(lifetime.tryBegin())
    }

    @Test fun idleDisposalReleasesExactlyOnce() {
        var releases = 0
        val lifetime = AnalysisLifetime { releases++ }
        lifetime.close()
        lifetime.close()
        assertEquals(1, releases)
        assertFalse(lifetime.tryBegin())
    }
}
