package com.bugsnag.performance

import android.os.SystemClock

internal object BugsnagClock {
    /**
     * The nanosecond precise unix time that the device booted at according to [SystemClock.elapsedRealtimeNanos].
     * This is not a perfect offset, but will be accurate to around a millisecond.
     */
    private val bootTimeNano =
        (System.currentTimeMillis() * 1_000_000) - SystemClock.elapsedRealtimeNanos()

    /**
     * Covert a given time from [SystemClock.elapsedRealtimeNanos] into Unix time with nanosecond
     * precision.
     */
    fun elapsedNanosToUnixTime(elapsedRealtimeNanos: Long) = elapsedRealtimeNanos + bootTimeNano
}