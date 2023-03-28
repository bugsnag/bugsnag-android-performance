package com.bugsnag.android.performance.internal

import android.os.SystemClock
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.testSpanProcessor
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

        val tracker = SpanTracker()
        val loadSpan = tracker.associate("TestActivity") {
            createSpan()
        }

        val onCreateSpan = tracker.associate("TestActivity", ViewLoadPhase.CREATE) {
            createSpan()
        }

        tracker.markSpanAutomaticEnd("TestActivity", ViewLoadPhase.CREATE)
        tracker.markSpanLeaked("TestActivity", ViewLoadPhase.CREATE)

        assertEquals(autoEndTime, onCreateSpan.endTime)
        assertNull(tracker["TestActivity", ViewLoadPhase.CREATE])

        assertNotNull(tracker["TestActivity"])
        assertNotEquals(autoEndTime, loadSpan.endTime)

        tracker.markSpanAutomaticEnd("TestActivity")
        tracker.markSpanLeaked("TestActivity")

        assertEquals(autoEndTime, loadSpan.endTime)
        assertNull(tracker["TestActivity"])
    }

    @Test
    fun testAutomaticEndWithManualEnd() = withStaticMock<SystemClock> { mockedClock ->
        val autoEndTime = 100L
        val realEndTime = 500L

        mockedClock.`when`<Long>(SystemClock::elapsedRealtimeNanos).thenReturn(autoEndTime)

        val tracker = SpanTracker()
        val loadSpan = tracker.associate("TestActivity") {
            createSpan()
        }

        val onCreateSpan = tracker.associate("TestActivity", ViewLoadPhase.CREATE) {
            createSpan()
        }

        // Check that subtoken spans can also be ended manually
        tracker.markSpanAutomaticEnd("TestActivity", ViewLoadPhase.CREATE)
        onCreateSpan.end(realEndTime)
        tracker.markSpanLeaked("TestActivity", ViewLoadPhase.CREATE)

        assertEquals(realEndTime, onCreateSpan.endTime)
        assertNull(tracker["TestActivity", ViewLoadPhase.CREATE])

        // Activity.onResume
        tracker.markSpanAutomaticEnd("TestActivity")

        // manually end the returned Span
        loadSpan.end(realEndTime)

        // Activity.onDestroy
        tracker.markSpanLeaked("TestActivity")

        assertEquals(realEndTime, loadSpan.endTime)
        assertNull(tracker["TestActivity"])
    }

    @Test
    fun testNormalEnd() {
        val endTime = 150L

        val tracker = SpanTracker()
        val loadSpan = tracker.associate("TestActivity") {
            createSpan()
        }

        val onCreateSpan = tracker.associate("TestActivity", ViewLoadPhase.CREATE) {
            createSpan()
        }

        assertSame(loadSpan, tracker["TestActivity"])
        assertSame(onCreateSpan, tracker["TestActivity", ViewLoadPhase.CREATE])

        // end the onCreate span
        tracker.endSpan("TestActivity", ViewLoadPhase.CREATE, endTime = endTime)

        assertEquals(endTime, onCreateSpan.endTime)
        assertNull(tracker["TestActivity", ViewLoadPhase.CREATE])
        assertSame(loadSpan, tracker["TestActivity"])

        // end the view load span
        tracker.endSpan("TestActivity", endTime = endTime)

        assertEquals(endTime, loadSpan.endTime)
        assertNull(tracker["TestActivity"])
    }

    private fun createSpan(): SpanImpl {
        return spanFactory.newSpan(processor = testSpanProcessor, endTime = null)
    }
}
