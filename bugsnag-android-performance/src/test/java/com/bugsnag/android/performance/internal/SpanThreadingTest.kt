package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.test.CollectingSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class SpanThreadingTest {
    private lateinit var spanFactory: TestSpanFactory
    private lateinit var spanProcessor: CollectingSpanProcessor

    @Before
    fun newSpanFactory() {
        spanFactory = TestSpanFactory()
        spanProcessor = CollectingSpanProcessor()
    }

    @Test
    fun threadSafeEnd() {
        val spans = (0..100).map { spanFactory.newSpan(endTime = null, processor = spanProcessor) }
        val latch = CountDownLatch(1)
        val threads = (0..Runtime.getRuntime().availableProcessors()).map { index ->
            thread(name = "Span-ender $index") {
                latch.await()
                spans.forEach { it.end(index + 100L) }
            }
        }

        // release the threads
        latch.countDown()

        // wait for all of the threads to complete processing
        threads.forEach { it.join() }

        val collectedSpans = spanProcessor.toList()
        assertEquals(spans.size, collectedSpans.size)
        assertTrue(collectedSpans.containsAll(spans))
    }
}
