package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Logger
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal interface Task {
    fun onAttach(worker: Worker) = Unit

    fun onDetach(worker: Worker) = Unit

    /**
     * Run the task and return `true` if any work was done (and more work is likely to be available
     * on the next call to `execute`).
     */
    fun execute(): Boolean
}

internal class Worker(
    private val tasks: List<Task>
) : Runnable {

    constructor(vararg tasks: Task) : this(tasks.toList())

    private val lock = ReentrantLock(false)
    private val workerWaitCondition = lock.newCondition()

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

        if (waitForTermination) {
            runner?.join()
        }

        runner = null
    }

    override fun run() {
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

    private fun attachTasks() {
        tasks.forEach { it.onAttach(this) }
    }

    private fun detachWorkers() {
        tasks.forEach { it.onDetach(this) }
    }

    fun wake() {
        if (!running) return
        // wake up with worker
        lock.withLock { workerWaitCondition.signalAll() }
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
            workerWaitCondition.await(
                InternalDebug.spanBatchTimeoutMs,
                TimeUnit.MILLISECONDS
            )
        }
    }
}
