package com.bugsnag.android.performance.internal.processing

import com.bugsnag.android.performance.test.TestSpanFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.concurrent.thread

class BatchingSpanProcessorTest {
    private lateinit var spanFactory: TestSpanFactory

    private lateinit var batchingSpanProcessor: BatchingSpanProcessor

    @Before
    fun setup() {
        spanFactory = TestSpanFactory()
        batchingSpanProcessor = BatchingSpanProcessor()
    }

    @Test
    fun testEmptyBatch() {
        assertTrue(batchingSpanProcessor.takeBatch().isEmpty())
    }

    @Test
    fun testSingleSpan() {
        val span = spanFactory.newSpan(endTime = { it + 1L }, processor = batchingSpanProcessor)
        val batch = batchingSpanProcessor.takeBatch()

        assertEquals(1, batch.size)
        assertSame(span, batch.first())
    }

    @Test
    fun testThreadedBatch() {
        val spansPerThread = 100
        val threads = (0 until 10).map { threadId ->
            thread {
                repeat(spansPerThread) { spanIndex ->
                    spanFactory.newSpan(
                        name = "$threadId/$spanIndex",
                        endTime = { it + spanIndex },
                        processor = batchingSpanProcessor,
                    )
                }
            }
        }

        threads.forEach { it.join() }

        val batch = batchingSpanProcessor.takeBatch()
        assertEquals(spansPerThread * threads.size, batch.size)

        val names = batch.mapTo(HashSet()) { it.name }
        assertEquals("duplicate or spuriously additional spans detected", batch.size, names.size)

        threads.indices.forEach { threadId ->
            repeat(spansPerThread) { spanIndex ->
                assertTrue(
                    "could not find span for threadId=$threadId, spanIndex=$spanIndex",
                    names.remove("$threadId/$spanIndex"),
                )
            }
        }
    }
}
