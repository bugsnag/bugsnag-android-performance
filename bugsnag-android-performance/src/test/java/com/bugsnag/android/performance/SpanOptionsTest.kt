package com.bugsnag.android.performance

import android.os.SystemClock
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn

class SpanOptionsTest {
    @Test
    fun testEquals() {
        assertEquals(
            // doesn't actually change the output
            SpanOptions.defaults.makeCurrentContext(SpanOptions.defaults.makeContext),
            SpanOptions.defaults
        )

        assertEquals(
            SpanOptions.defaults.makeCurrentContext(true),
            SpanOptions.defaults
        )

        assertNotEquals(
            SpanOptions.defaults.makeCurrentContext(false),
            SpanOptions.defaults
        )

        assertNotEquals(
            SpanOptions.defaults.setFirstClass(true),
            SpanOptions.defaults
        )

        assertEquals(
            SpanOptions.defaults.startTime(12345L),
            SpanOptions.defaults.startTime(12345L)
        )

        assertNotEquals(
            SpanOptions.defaults.startTime(12345L),
            SpanOptions.defaults
        )

        assertNotEquals(
            SpanOptions.defaults.startTime(12345L),
            SpanOptions.defaults.startTime(54321L)
        )

        assertEquals(
            SpanOptions.defaults.within(SpanContext.invalid),
            SpanOptions.defaults.within(SpanContext.invalid)
        )

        assertEquals(
            SpanOptions.defaults.setFirstClass(true),
            SpanOptions.defaults.setFirstClass(true),
        )

        assertNotEquals(
            SpanOptions.defaults.within(SpanContext.invalid),
            SpanOptions.defaults
        )
    }

    @Test
    fun defaultStartTime() = withStaticMock<SystemClock> { clock ->
        val expectedTime = 192837465L
        clock.`when`<Long> { SystemClock.elapsedRealtimeNanos() }.doReturn(expectedTime)
        assertEquals(expectedTime, SpanOptions.defaults.startTime)
    }

    @Test
    fun overrideStartTime() {
        val time = 876123L
        assertEquals(time, SpanOptions.defaults.startTime(time).startTime)
        // test multi overrides of the same value
        assertEquals(time, SpanOptions.defaults.startTime(0L).startTime(time).startTime)
    }

    @Test
    fun overrideIsFirstClass() {
        assertFalse(SpanOptions.defaults.setFirstClass(false).isFirstClass)
        // test multi overrides of the same value
        assertTrue(SpanOptions.defaults.setFirstClass(false).setFirstClass(true).isFirstClass)
    }
}
