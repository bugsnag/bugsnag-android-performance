package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.test.CollectingSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpanBlockingTest {
    private lateinit var spanFactory: TestSpanFactory

    private lateinit var spanProcessor: CollectingSpanProcessor

    @Before
    fun setUp() {
        spanFactory = TestSpanFactory()
        spanProcessor = CollectingSpanProcessor()
    }

    @Test
    fun blockingSpanConditions_CancelledConditions() {
        val span = spanFactory.newSpan("Test", endTime = null, processor = spanProcessor)
        assertTrue(span.isOpen())
        assertFalse(span.isBlocked())

        val condition = span.block(1000L)
        assertNotNull(condition)
        assertFalse(span.isEnded())
        assertTrue(span.isBlocked())

        // end the span with an expected time
        span.end(123L)

        assertTrue(span.isEnded())
        assertTrue(span.isBlocked())
        assertFalse(span.isOpen())
        assertTrue(spanProcessor.isEmpty())
        assertEquals(123L, span.endTime)

        condition!!.cancel()
        assertEquals(123L, span.endTime)
        assertTrue(spanProcessor.isNotEmpty())
        assertSame(span, spanProcessor.toList().single())
    }

    @Test
    fun blockingSpanConditions_DoubleClose() {
        val span = spanFactory.newSpan("Test", endTime = null, processor = spanProcessor)
        assertTrue(span.isOpen())
        assertFalse(span.isBlocked())

        val condition1 = span.block(1000L)
        assertNotNull(condition1)
        val condition2 = span.block(1000L)
        assertNotNull(condition2)

        // end the span with an expected time
        span.end(123L)

        assertTrue(span.isEnded())
        assertTrue(span.isBlocked())
        assertFalse(span.isOpen())
        assertTrue(spanProcessor.isEmpty())
        assertEquals(123L, span.endTime)

        condition2?.upgrade()
        condition2!!.close(321L)
        condition2.close(987L) // this should be ignored
        assertEquals(321L, span.endTime)
        assertTrue(spanProcessor.isEmpty())

        condition1?.upgrade()
        condition1!!.close(456L)
        assertEquals(456L, span.endTime)
        assertTrue(spanProcessor.isNotEmpty())
        assertSame(span, spanProcessor.toList().single())
    }

    @Test
    fun blockingSpanConditions_Timeout() {
        val span = spanFactory.newSpan("Test", endTime = null, processor = spanProcessor)
        assertTrue(span.isOpen())
        assertFalse(span.isBlocked())

        val condition1 = span.block(5L)
        assertNotNull(condition1)

        assertFalse(span.isEnded())
        assertTrue(span.isBlocked())
        assertTrue(spanProcessor.isEmpty())

        // end the span with an expected time
        span.end(123L)
        assertEquals(123L, span.endTime)

        assertTrue(span.isEnded())
        assertTrue(span.isBlocked())
        assertTrue(spanProcessor.isEmpty())

        // trigger the timeout
        spanFactory.timeoutExecutor.runAllTimeouts()

        assertTrue(span.isEnded())
        assertFalse(span.isBlocked())
        assertTrue(spanProcessor.isNotEmpty())
        assertSame(span, spanProcessor.toList().single())
    }

    @Test
    fun discardBlockedSpan() {
        val span = spanFactory.newSpan("Test", endTime = null, processor = spanProcessor)
        assertTrue(span.isOpen())
        assertFalse(span.isBlocked())

        val condition1 = span.block(1000L)
        assertNotNull(condition1)
        assertFalse(span.isEnded())
        assertTrue(span.isBlocked())
        assertTrue(spanProcessor.isEmpty())

        span.discard()
        assertFalse(span.isOpen())
        assertFalse(span.isBlocked())
        assertTrue(spanProcessor.isEmpty())

        assertNull(condition1!!.upgrade())

        condition1.close()
        assertFalse(span.isOpen())
        assertFalse(span.isBlocked())
        assertTrue(spanProcessor.isEmpty())
    }
}
