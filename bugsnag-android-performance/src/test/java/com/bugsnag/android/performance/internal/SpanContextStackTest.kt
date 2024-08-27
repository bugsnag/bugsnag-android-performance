package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class SpanContextStackTest {
    private lateinit var spanFactory: TestSpanFactory

    @Before
    fun setUp() {
        spanFactory = TestSpanFactory()
    }

    @Test
    fun currentCannotBeEnded() {
        val stack = SpanContextStack()
        val parent = spanFactory.newSpan(endTime = null, processor = NoopSpanProcessor)
        stack.attach(parent)

        val child = spanFactory.newSpan(
            endTime = null,
            parentSpanId = parent.spanId,
            traceId = parent.traceId,
            processor = NoopSpanProcessor,
        )
        stack.attach(child)

        assertSame(child, stack.top)
        child.end(1L)
        assertSame(parent, stack.top)
    }

    @Test
    fun weakReferenced() {
        val stack = SpanContextStack()
        val parent = spanFactory.newSpan(endTime = null, processor = NoopSpanProcessor)
        stack.attach(parent)

        var child: SpanImpl? = spanFactory.newSpan(
            endTime = null,
            parentSpanId = parent.spanId,
            traceId = parent.traceId,
            processor = NoopSpanProcessor,
        )
        stack.attach(child!!)
        assertSame(child, stack.top)

        // release the child reference, and GC (fingers-crossed that the GC does its job)
        @Suppress("UNUSED_VALUE")
        child = null
        @Suppress("ExplicitGarbageCollectionCall") // can we call this best-effort unit testing?
        System.gc()

        assertSame(parent, stack.top)
    }
}
