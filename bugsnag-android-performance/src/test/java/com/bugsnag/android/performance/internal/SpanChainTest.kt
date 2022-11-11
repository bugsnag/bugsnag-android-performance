package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.testSpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        span1.previous = span2

        val filteredChain = span1.filter { it.id != 1L }

        assertSame(span2, filteredChain)
        assertNull(filteredChain!!.previous)
    }

    @Test
    fun filterItems() {
        val spans = spanFactory.newSpans(10, testSpanProcessor)
        val root = spans.toSpanChain()

        val filtered = root.filter { it.id % 2 == 0L }!!.toList()
        assertEquals(5, filtered.size)
        // SpanFactory starts counting at 1
        assertSame(spans[1], filtered[0]) // id == 2
        assertSame(spans[3], filtered[1]) // id == 4
        assertSame(spans[5], filtered[2]) // id == 6
        assertSame(spans[7], filtered[3]) // id == 8
        assertSame(spans[9], filtered[4]) // id == 10
    }

    @Test
    fun filterAllItems() {
        val spans = spanFactory.newSpans(10, testSpanProcessor)
        val root = spans.toSpanChain()

        val filtered = root.filter { it.id == -1L }
        assertNull(filtered)
    }

    @Test
    fun filterNothing() {
        val spans = spanFactory.newSpans(10, testSpanProcessor)
        val root = spans.toSpanChain()

        val filtered = root.filter { it.id != -1L }!!.toList()
        assertEquals(spans, filtered)
    }

    @Test
    fun size() {
        val spanList = spanFactory.newSpans(25, testSpanProcessor)
        var chain: Span? = spanList.toSpanChain()

        // assert the chain size is the same for each link in the chain
        repeat(spanList.size) { index ->
            assertEquals(spanList.size - index, chain?.size)
            chain = chain?.previous
        }
    }

    @Test
    fun emptyChecks() {
        val nullSpan: Span? = null
        val notNull: Span? = spanFactory.newSpan(processor = testSpanProcessor)

        assertTrue(nullSpan.isEmpty())
        assertTrue(notNull.isNotEmpty())

        assertFalse(nullSpan.isNotEmpty())
        assertFalse(notNull.isEmpty())
    }

    @Test
    fun concatChains() {
        val spanList1 = spanFactory.newSpans(6, testSpanProcessor)
        val spanList2 = spanFactory.newSpans(6, testSpanProcessor)

        val chain = spanList1.toSpanChain() + spanList2.toSpanChain()

        assertSame(chain, spanList1[0])
        assertEquals(12, chain.size)

        val joinedChains = chain.toList()
        assertEquals((spanList1 + spanList2).toList(), joinedChains)
    }

    private fun List<Span>.toSpanChain(): Span {
        reduce { acc, span -> span.also { acc.previous = span } }
        return first()
    }
}
