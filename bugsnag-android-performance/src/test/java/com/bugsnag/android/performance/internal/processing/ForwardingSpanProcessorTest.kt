package com.bugsnag.android.performance.internal.processing

import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.test.TestSpanFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class ForwardingSpanProcessorTest {
    private lateinit var spanFactory: TestSpanFactory

    private lateinit var spanProcessor: ForwardingSpanProcessor

    private lateinit var collectedSpans: MutableList<Span>

    @Before
    fun setup() {
        spanFactory = TestSpanFactory()
        spanProcessor = ForwardingSpanProcessor()
        collectedSpans = ArrayList()
    }

    @Test
    fun endAfterForwarding() {
        // one opened-and-closed Span
        val span1 = spanFactory.newSpan(processor = spanProcessor)

        // one "in-flight" span
        val span2 = spanFactory.newSpan(processor = spanProcessor, endTime = null)

        // forward the closed span (and any future spans)
        spanProcessor.forwardTo(collectedSpans::add)

        // end the "in-flight" span (it should be forwarded to collectedSpans)
        span2.end(endTime = 10L)

        // open and close another span for safe-measure (it should by forwarded to collectedSpans)
        val span3 = spanFactory.newSpan(processor = spanProcessor)

        assertEquals(3, collectedSpans.size)
        assertSame(span1, collectedSpans[0])
        assertSame(span2, collectedSpans[1])
        assertSame(span3, collectedSpans[2])
    }
}
