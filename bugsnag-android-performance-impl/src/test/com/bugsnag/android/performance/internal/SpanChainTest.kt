package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SpanChainTest {
    private lateinit var spanFactory: TestSpanFactory

    @Before
    fun setupDependencies() {
        spanFactory = TestSpanFactory()
    }

    @Test
    fun unlinkTo() {
        val spans = spanFactory.newSpans(10, NoopSpanProcessor)
        val root = spans.toSpanChain()

        // check that all the spans (except the last) have a `next` link
        (0 until spans.lastIndex).forEach { index -> assertNotNull(spans[index].next) }

        val unlinked = root.unlinkTo(arrayListOf())

        // ensure all of the links have been cleared
        unlinked.forEach { assertNull(it.next) }

        // the output list should be the same as the input list at this point
        assertEquals(spans, unlinked)
    }

    private fun List<SpanImpl>.toSpanChain(): SpanChain {
        reduce { acc, span -> span.also { acc.next = span } }
        return first()
    }
}
