package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.testSpanProcessor
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
    fun testAutomaticEndWithLeak() {
        val autoEndTime = 100L

        val tracker = SpanTracker<String>()
        val span = tracker.track("TestActivity") {
            spanFactory.newSpan(
                processor = testSpanProcessor,
                endTime = null
            )
        }

        tracker.markSpanAutomaticEnd("TestActivity", autoEndTime)
        tracker.markSpanLeaked("TestActivity")

        assertEquals(autoEndTime, span.endTime)
        assertNull(tracker["TestActivity"])
    }

    @Test
    fun testAutomaticEndWithManualEnd() {
        val autoEndTime = 100L
        val realEndTime = 500L

        val tracker = SpanTracker<String>()
        val span = tracker.track("TestActivity") {
            spanFactory.newSpan(processor = testSpanProcessor, endTime = null)
        }

        // Activity.onResume
        tracker.markSpanAutomaticEnd("TestActivity", autoEndTime)

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
