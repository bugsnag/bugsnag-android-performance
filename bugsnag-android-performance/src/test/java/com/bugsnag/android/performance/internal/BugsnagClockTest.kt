package com.bugsnag.android.performance.internal

import android.os.SystemClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BugsnagClockTest {
    @Test
    fun testZero() {
        assertEquals(BugsnagClock.bootTimeNano, BugsnagClock.elapsedNanosToUnixTime(0L))
    }

    @Test
    fun testCurrentTime() {
        val elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        assertTrue(BugsnagClock.elapsedNanosToUnixTime(elapsedRealtimeNanos) > elapsedRealtimeNanos)
    }
}