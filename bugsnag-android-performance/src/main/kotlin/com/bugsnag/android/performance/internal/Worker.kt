package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Logger
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal interface Task {
    fun onAttach(worker: Worker) = Unit

    fun onDetach(worker: Worker) = Unit

    /**
     * Run the task and return `true` if work was done successfully (and more work is likely to be
     * available on the next call to `execute`).
     */
    fun execute(): Boolean
}

internal abstract class AbstractTask : Task {
    protected var worker: Worker? = null
        private set

    override fun onAttach(worker: Worker) {
        this.worker = worker
    }

    override fun onDetach(worker: Worker) {
        this.worker = worker
    }
}

internal class Worker(
    /**
     * A list of `Runnable`s that need to be run (once each) before the worker tasks are run.
     * These are typically IO related startup tasks that need to be completed before any spans
     * are actually sent.
     */
    startupTasks: List<Runnable>,
    private val tasks: List<Task>,
) : Runnable {

    private var startupTasks: List<Runnable>? = startupTasks

    private val lock = ReentrantLock(false)
    private val wakeWorker = lock.newCondition()

    /**
     * This avoids us having to do all of the work under `lock` allowing `wake` to be called between
     * the time to deciding to "wait for work" and actually entering `lock` to go to sleep. This
     * allows a call to [wake] to override the decision taken by the main loop, forcing an
     * additional work iteration instead of going to sleep.
     */
    private var wakeIsPending = false

    private var runner: Thread? = null

    @Volatile
    private var running = false

    @Synchronized
    fun start() {
        if (running) return

        running = true

        runner = Thread(this, "Bugsnag Performance").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun stop(waitForTermination: Boolean = true) {
        if (!running) return

        running = false
        runner?.interrupt()

        try {
            if (waitForTermination) {
                runner?.join()
            }
        } finally {
            runner = null
        }
    }

    override fun run() {
        runStartupTasks()

        attachTasks()
        try {
            while (running) {
                val shouldWaitForWork = runTasks()

                if (shouldWaitForWork) {
                    waitForWorkOrWakeup()
                }
            }
        } finally {
            detachWorkers()
        }
    }

    private fun runStartupTasks() {
        startupTasks?.forEach { it.run() }

        // release the startup tasks so we don't hog the memory for ever
        startupTasks = null
    }

    private fun attachTasks() {
        tasks.forEach { task ->
            try {
                task.onAttach(this)
            } catch (e: Exception) {
                Logger.w("unhandled exception while attempting to attach worker $task", e)
            }
        }
    }

    private fun detachWorkers() {
        tasks.forEach { task ->
            try {
                task.onDetach(this)
            } catch (e: Exception) {
                Logger.w("unhandled exception while attempting to detach worker $task", e)
            }
        }
    }

    fun wake() {
        if (!running) return
        // wake up with worker
        lock.withLock {
            wakeIsPending = true
            wakeWorker.signalAll()
        }
    }

    private fun runTasks(): Boolean {
        var shouldWaitForWork = true

        for (task in tasks) {
            try {
                if (task.execute()) {
                    shouldWaitForWork = false
                }
            } catch (ex: Exception) {
                // failures are treated as a Task returning false
                Logger.w("unhandled exception in a worker task: $task", ex)
            }
        }

        return shouldWaitForWork
    }

    private fun waitForWorkOrWakeup() {
        lock.withLock {
            if (!wakeIsPending) {
                try {
                    wakeWorker.await(InternalDebug.workerSleepMs, TimeUnit.MILLISECONDS)
                } catch (ie: InterruptedException) {
                    // ignore these, we treat interrupts as wake-ups
                }
            }

            wakeIsPending = false
        }
    }
}
