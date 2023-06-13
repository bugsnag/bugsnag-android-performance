package com.bugsnag.android.performance.internal

import android.os.Build
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import java.util.Date
import kotlin.math.max

internal object BugsnagClock {
    private const val NANOS_IN_MILLIS = 1_000_000L

    /**
     * The nanosecond precise unix time that the device booted at according to
     * [SystemClock.elapsedRealtimeNanos]. This is not a perfect offset, but will be accurate
     * to around a millisecond.
     */
    @VisibleForTesting
    internal val bootTimeNano: Long = safeBootTimeNanos()

    @VisibleForTesting
    internal fun safeBootTimeNanos(): Long {
        // Start off by trying to use the GNSS clock - as this should be more reliable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // We can possibly fallback to the location-providers clock
            // Which should be very nice and accurate - if it's available and has actually synched up
            try {
                val gnssInstant = SystemClock.currentGnssTimeClock().instant()
                val currentNanos = gnssInstant.toEpochMilli() + gnssInstant.nano

                val bootTime = currentNanos - SystemClock.elapsedRealtimeNanos()
                if (bootTime > 0L) {
                    return bootTime
                }
            } catch (ex: Exception) {
                // ignore this and fallback
            }
        }

        // We couldn't use the GNSS clock, so we try and use the wall clock
        val bootTime = (System.currentTimeMillis() * NANOS_IN_MILLIS) -
            SystemClock.elapsedRealtimeNanos()

        // If the elapsedRealtimeNanos is (somehow) longer than the wall clock time, we would
        // return a negative boot time possibly leading to negative timestamps in the Spans
        //
        // If this is detected, we fallback to a zero boot time. In this state we will report
        // the elapsedRealtimeNanos directly which is not the expected unix-nano time but
        // will at least remain positive
        return max(bootTime, 0L)
    }

    fun toDate(): Date = Date(currentUnixNanoTime() / NANOS_IN_MILLIS)

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
