package com.bugsnag.android.performance.internal

import android.os.SystemClock
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.testSpanProcessor
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SpanTrackerTest {
    private lateinit var spanFactory: TestSpanFactory

    @Before
    fun createSpanFactory() {
        spanFactory = TestSpanFactory()
    }

    @Test
    fun testAutomaticEndWithLeak() = withStaticMock<SystemClock> { mockedClock ->
        val autoEndTime = 100L
        mockedClock.`when`<Long>(SystemClock::elapsedRealtimeNanos).thenReturn(autoEndTime)

        val tracker = SpanTracker<String>()
        val span = tracker.track("TestActivity") {
            spanFactory.newSpan(
                processor = testSpanProcessor,
                endTime = null
            )
        }

        tracker.markSpanAutomaticEnd("TestActivity")
        tracker.markSpanLeaked("TestActivity")

        assertEquals(autoEndTime, span.endTime)
        assertNull(tracker["TestActivity"])
    }

    @Test
    fun testAutomaticEndWithManualEnd() = withStaticMock<SystemClock> { mockedClock ->
        val autoEndTime = 100L
        val realEndTime = 500L

        mockedClock.`when`<Long>(SystemClock::elapsedRealtimeNanos).thenReturn(autoEndTime)

        val tracker = SpanTracker<String>()
        val span = tracker.track("TestActivity") {
            spanFactory.newSpan(processor = testSpanProcessor, endTime = null)
        }

        // Activity.onResume
        tracker.markSpanAutomaticEnd("TestActivity")

        // manually end the returned Span
        span.end(realEndTime)

        // Activity.onDestroy
        tracker.markSpanLeaked("TestActivity")

        assertEquals(realEndTime, span.endTime)
        assertNull(tracker["TestActivity"])
    }

    @Test
    fun testNormalEnd() {
        val endTime = 150L

        val tracker = SpanTracker<String>()
        val span = tracker.track("TestActivity") {
            spanFactory.newSpan(processor = testSpanProcessor, endTime = null)
        }

        tracker.endSpan("TestActivity", endTime)

        assertEquals(endTime, span.endTime)
        assertNull(tracker["TestActivity"])
    }
}
