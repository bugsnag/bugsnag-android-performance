package com.bugsnag.android.performance.internal

import android.os.SystemClock
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
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

    @Test
    fun testSafeBootFallback_Zero() = withStaticMock<SystemClock> { mockedClock ->
        mockedClock.`when`<Long> { SystemClock.elapsedRealtimeNanos() } doReturn Long.MAX_VALUE
        assertEquals(0L, BugsnagClock.safeBootTimeNanos())
    }
}
