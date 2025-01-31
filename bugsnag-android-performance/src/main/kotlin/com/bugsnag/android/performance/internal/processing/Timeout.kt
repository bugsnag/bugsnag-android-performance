package com.bugsnag.android.performance.internal.processing

import android.os.SystemClock
import java.util.concurrent.TimeUnit

internal interface Timeout : ScheduledAction {
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
}

internal interface TimeoutExecutor {
    fun scheduleTimeout(timeout: Timeout)
    fun cancelTimeout(timeout: Timeout)
}
