package com.artkachenko.livetextfinder.camera

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/** Allows one native frame copy across camera-session replacement without queuing stale work. */
internal object CaptureCopyWorker : Executor {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "camera-frame-copy").apply { isDaemon = true }
    }
    private val busy = AtomicBoolean(false)

    override fun execute(command: Runnable) {
        if (!busy.compareAndSet(false, true)) {
            throw RejectedExecutionException("A camera frame copy is already in progress.")
        }
        try {
            executor.execute {
                try {
                    command.run()
                } finally {
                    busy.set(false)
                }
            }
        } catch (error: RuntimeException) {
            busy.set(false)
            throw error
        }
    }
}
