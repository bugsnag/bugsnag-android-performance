package com.bugsnag.android.performance.internal.processing

import android.os.SystemClock
import com.bugsnag.android.performance.Logger
import java.util.concurrent.DelayQueue
import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit

internal interface Timeout : Runnable, Delayed {
    /**
     * The time that this `Timeout` is "due" relative to the [SystemClock.elapsedRealtime] clock.
     */
    val target: Long

    /**
     * Return the number of milliseconds before this Timeout has elapsed (may be `<=0` if the
     * timeout has already passed).
     */
    val relativeMs: Long
        get() = target - SystemClock.elapsedRealtime()

    override fun getDelay(unit: TimeUnit): Long {
        return unit.convert(relativeMs, TimeUnit.MILLISECONDS)
    }

    override fun compareTo(other: Delayed): Int {
        val delay = getDelay(TimeUnit.NANOSECONDS)
        val otherDelay = other.getDelay(TimeUnit.NANOSECONDS)
        return when {
            delay < otherDelay -> -1
            delay > otherDelay -> 1
            else -> 0
        }
    }
}

internal interface TimeoutExecutor {
    fun scheduleTimeout(timeout: Timeout)
    fun cancelTimeout(timeout: Timeout)
}

internal class TimeoutExecutorImpl : Runnable, TimeoutExecutor {
    @Volatile
    private var running = false

    private var thread: Thread? = null

    private val timeouts = DelayQueue<Timeout>()

    @Synchronized
    fun start() {
        if (running) {
            return
        }

        running = true
        thread = Thread(this, "Bugsnag Timeouts Thread")
        thread?.start()
    }

    @Synchronized
    fun stop() {
        if (!running) {
            return
        }

        running = false
        thread?.interrupt()
        thread = null
    }

    override fun run() {
        while (running) {
            try {
                timeouts.take().run()
            } catch (ie: InterruptedException) {
                // ignore and continue, the thread may have been stopped
            } catch (ex: Exception) {
                Logger.w("unhandled exception in timeout", ex)
            }
        }
    }

    override fun scheduleTimeout(timeout: Timeout) {
        timeouts.add(timeout)
    }

    override fun cancelTimeout(timeout: Timeout) {
        timeouts.remove(timeout)
    }
}
