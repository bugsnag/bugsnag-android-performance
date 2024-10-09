package com.bugsnag.android.performance.internal.processing

import android.os.SystemClock

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

internal class CollectingTimeoutExecutor : TimeoutExecutor {
    private val timeouts = HashSet<Timeout>()

    @Synchronized
    fun drainTo(timeoutExecutor: TimeoutExecutor) {
        timeouts.forEach(timeoutExecutor::scheduleTimeout)
        timeouts.clear()
    }

    @Synchronized
    override fun scheduleTimeout(timeout: Timeout) {
        timeouts.add(timeout)
    }

    @Synchronized
    override fun cancelTimeout(timeout: Timeout) {
        timeouts.remove(timeout)
    }
}
