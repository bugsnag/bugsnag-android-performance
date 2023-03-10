package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.testSpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpanChainTest {
    private lateinit var spanFactory: TestSpanFactory

    @Before
    fun setupDependencies() {
        spanFactory = TestSpanFactory()
    }

    @Test
    fun filterFirstItem() {
        val span1 = spanFactory.newSpan(spanId = 1L, processor = testSpanProcessor)
        val span2 = spanFactory.newSpan(spanId = 2L, processor = testSpanProcessor)

        span1.next = span2

        val filteredChain = span1.filter { it.spanId != 1L }

        assertSame(span2, filteredChain)
        assertNull(filteredChain!!.next)
    }

    @Test
    fun filterItems() {
        val spans = spanFactory.newSpans(10, testSpanProcessor)
        val root = spans.toSpanChain()

        val filtered = root.filter { it.spanId % 2 == 0L }!!.toList()
        assertEquals(5, filtered.size)
        // SpanFactory starts counting at 1
        assertSame(spans[1], filtered[0]) // id == 2
        assertSame(spans[3], filtered[1]) // id == 4
        assertSame(spans[5], filtered[2]) // id == 6
        assertSame(spans[7], filtered[3]) // id == 8
        assertSame(spans[9], filtered[4]) // id == 10
    }

    @Test
    fun tail() {
        val spans = spanFactory.newSpans(10, testSpanProcessor)
        val root = spans.toSpanChain()

        assertSame(spans.last(), root.tail())
    }

    @Test
    fun filterAllItems() {
        val spans = spanFactory.newSpans(10, testSpanProcessor)
        val root = spans.toSpanChain()

        val filtered = root.filter { it.spanId == -1L }
        assertNull(filtered)
    }

    @Test
    fun filterNothing() {
        val spans = spanFactory.newSpans(10, testSpanProcessor)
        val root = spans.toSpanChain()

        val filtered = root.filter { it.spanId != -1L }!!.toList()
        assertEquals(spans, filtered)
    }

    @Test
    fun size() {
        val spanList = spanFactory.newSpans(25, testSpanProcessor)
        var chain: SpanChain? = spanList.toSpanChain()

        // assert the chain size is the same for each link in the chain
        repeat(spanList.size) { index ->
            assertEquals(spanList.size - index, chain?.size)
            chain = chain?.next
        }
    }

    @Test
    fun emptyChecks() {
        val nullSpan: SpanChain? = null
        val notNull: SpanChain = spanFactory.newSpan(processor = testSpanProcessor)

        assertTrue(nullSpan.isEmpty())
        assertTrue(notNull.isNotEmpty())

        assertFalse(nullSpan.isNotEmpty())
        assertFalse(notNull.isEmpty())
    }

    @Test
    fun unlinkTo() {
        val spans = spanFactory.newSpans(10, testSpanProcessor)
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
