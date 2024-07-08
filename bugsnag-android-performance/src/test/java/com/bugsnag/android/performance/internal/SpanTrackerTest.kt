package com.bugsnag.android.performance.internal

import android.os.SystemClock
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

        assertEquals(autoEndTime, onCreateSpan.endTime.get())
        assertNull(tracker["TestActivity", ViewLoadPhase.CREATE])

        assertNotNull(tracker["TestActivity"])
        assertNotEquals(autoEndTime, loadSpan.endTime.get())

        tracker.markSpanAutomaticEnd("TestActivity")
        tracker.markSpanLeaked("TestActivity")

        assertEquals(autoEndTime, loadSpan.endTime.get())
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

        assertEquals(realEndTime, onCreateSpan.endTime.get())
        assertNull(tracker["TestActivity", ViewLoadPhase.CREATE])

        // Activity.onResume
        tracker.markSpanAutomaticEnd("TestActivity")

        // manually end the returned Span
        loadSpan.end(realEndTime)

        // Activity.onDestroy
        tracker.markSpanLeaked("TestActivity")

        assertEquals(realEndTime, loadSpan.endTime.get())
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

        assertEquals(endTime, onCreateSpan.endTime.get())
        assertNull(tracker["TestActivity", ViewLoadPhase.CREATE])
        assertSame(loadSpan, tracker["TestActivity"])

        // end the view load span
        tracker.endSpan("TestActivity", endTime = endTime)

        assertEquals(endTime, loadSpan.endTime.get())
        assertNull(tracker["TestActivity"])
    }

    @Test
    fun testDenseTrackedSpans() {
        val tokens = Array(16) { Any() }
        val tracker = SpanTracker()
        val trackedSpans = populateDenseSpanTracker(tokens, tracker)

        assertTrackerSpans(tracker, trackedSpans)
    }

    @Test
    fun testRemoveAssociation() {
        val tokens = Array(16) { Any() }
        val tracker = SpanTracker()
        val trackedSpans = populateDenseSpanTracker(tokens, tracker)

        tracker.removeAssociation(tokens[5])
        tracker.removeAssociation(tokens[6], ViewLoadPhase.RESUME)

        trackedSpans.keys.removeAll { (token, _) -> token === tokens[5] }
        trackedSpans.keys.remove(tokens[6] to ViewLoadPhase.RESUME)

        assertTrackerSpans(tracker, trackedSpans)
    }

    @Test
    fun testEndAllSpans() {
        val tokens = Array(16) { Any() }
        val tracker = SpanTracker()
        val trackedSpans = populateDenseSpanTracker(tokens, tracker)

        val endedToken = tokens[3] // it doesn't really matter which we choose
        tracker.endAllSpans(endedToken, 1234L)

        val rootSpan = trackedSpans.remove(endedToken to null)!!
        assertTrue(rootSpan.isEnded())
        assertEquals(1234L, rootSpan.endTime.get())
        assertNull(tracker[endedToken])

        ViewLoadPhase.values().forEach { phase ->
            val span = trackedSpans.remove(endedToken to phase)!!
            assertTrue(span.isEnded())
            assertEquals(1234L, span.endTime.get())
            assertNull(tracker[endedToken, phase])
        }

        assertTrackerSpans(tracker, trackedSpans)
    }

    private fun createSpan(name: String): SpanImpl {
        return spanFactory.newSpan(name, processor = NoopSpanProcessor, endTime = null)
    }

    private fun createSpan(): SpanImpl {
        return spanFactory.newSpan(processor = NoopSpanProcessor, endTime = null)
    }

    private fun assertTrackerSpans(
        tracker: SpanTracker,
        expectedSpans: Map<Pair<Any, ViewLoadPhase?>, SpanImpl>,
    ) {
        expectedSpans.entries.forEach { entry ->
            val (token, subToken) = entry.key
            assertSame(entry.value, tracker[token, subToken])
        }
    }

    private fun populateDenseSpanTracker(
        tokens: Array<out Any>,
        tracker: SpanTracker,
    ): MutableMap<Pair<Any, ViewLoadPhase?>, SpanImpl> {
        val spans = HashMap<Pair<Any, ViewLoadPhase?>, SpanImpl>()

        tokens.forEach { token ->
            val rootTokenSpan = createSpan(token.toString())
            assertSame(rootTokenSpan, tracker.associate(token, null, rootTokenSpan))
            spans[token to null] = rootTokenSpan

            ViewLoadPhase.values().forEach { phase ->
                val span = createSpan("$token/$phase")
                val boundSpan = tracker.associate(token, phase, span)

                assertSame(span, boundSpan)

                spans[token to phase] = span
            }
        }

        return spans
    }
}
