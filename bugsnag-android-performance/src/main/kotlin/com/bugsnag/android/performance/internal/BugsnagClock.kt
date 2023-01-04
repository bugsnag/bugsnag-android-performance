package com.bugsnag.android.performance.internal

import android.os.SystemClock
import androidx.annotation.VisibleForTesting

internal object BugsnagClock {
    private const val NANOS_IN_MILLIS = 1_000_000

    /**
     * The nanosecond precise unix time that the device booted at according to
     * [SystemClock.elapsedRealtimeNanos]. This is not a perfect offset, but will be accurate
     * to around a millisecond.
     */
    @VisibleForTesting
    internal val bootTimeNano =
        (System.currentTimeMillis() * NANOS_IN_MILLIS) - SystemClock.elapsedRealtimeNanos()

    /**
     * Covert a given time from [SystemClock.elapsedRealtimeNanos] into Unix time with nanosecond
     * precision.
     */
    fun elapsedNanosToUnixTime(elapsedRealtimeNanos: Long) = elapsedRealtimeNanos + bootTimeNano

    /**
     * `System.currentTimeMillis` but as nanosecond precision time.
     */
    fun currentUnixNanoTime(): Long = elapsedNanosToUnixTime(SystemClock.elapsedRealtimeNanos())
}
