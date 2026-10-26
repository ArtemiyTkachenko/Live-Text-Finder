package com.artkachenko.livetextfinder.camera

import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CaptureCopyWorkerTest {
    @Test
    fun anotherSessionCannotQueueCopyWhilePreviousNativeCopyIsStillRunning() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        CaptureCopyWorker.execute {
            try {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
            } finally {
                finished.countDown()
            }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertFailsWith<RejectedExecutionException> {
                CaptureCopyWorker.execute { error("A stale copy must never be queued.") }
            }
        } finally {
            release.countDown()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
        }
    }
}
