package com.bugsnag.android.performance

import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.test.CollectingSpanProcessor
import com.bugsnag.android.performance.test.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
internal class SpanContextTest {
    private lateinit var spanFactory: SpanFactory
    private lateinit var spanProcessor: CollectingSpanProcessor

    @Before
    fun ensureContextClear() {
        while (SpanContext.current != SpanContext.invalid) {
            SpanContext.detach(SpanContext.current)
        }
    }

    @Before
    fun newSpanFactory() {
        spanProcessor = CollectingSpanProcessor()
        spanFactory = SpanFactory(spanProcessor)
    }

    @Test
    fun invalidSpanContext() {
        assertSame(SpanContext.invalid, SpanContext.current)
    }

    @Test
    fun testThreadedContext() {
        (0..5)
            .map {
                task {
                    val threadId = Thread.currentThread().id
                    val expectedStack = mutableListOf<SpanContext>()
                    repeat(1000) {
                        val newContext = TestSpanContext(it.toLong(), UUID.randomUUID(), threadId)
                        SpanContext.attach(newContext)
                        expectedStack.add(newContext)

                        assertSame(newContext, SpanContext.current)
                    }

                    expectedStack.reversed().forEach { expectedContext ->
                        assertSame(expectedContext, SpanContext.current)
                        SpanContext.detach(expectedContext)
                    }
                }
            }
            .forEach { it.get() }
    }

    @Test
    fun spansClosedInOrder() {
        createTestSpan().use { spanA ->
            assertSame(spanA, SpanContext.current)
            createTestSpan().use { spanB ->
                assertSame(spanB, SpanContext.current)
                createTestSpan().use { spanC ->
                    assertSame(spanC, SpanContext.current)
                }
                assertSame(spanB, SpanContext.current)
            }
            assertSame(spanA, SpanContext.current)
        }
        assertSame(SpanContext.invalid, SpanContext.current)
    }

    @Test
    fun spansClosedOutOfOrder() {
        val spanA = createTestSpan()
        val spanB = createTestSpan()
        val spanC = createTestSpan()

        spanA.end(1L)
        spanB.end(2L)
        assertSame(spanC, SpanContext.current)

        spanC.end(3L)
        assertSame(SpanContext.invalid, SpanContext.current)
    }

    @Test
    fun currentContextCannotBeClosed() {
        // End the spans in a different thread so that they remain on the stack
        val spanA = createTestSpan()
        val spanB = createTestSpan()
        task {
            spanA.end()
            spanB.end()
        }.get()

        assertEquals(2, SpanContext.contextStack.size)

        // SpanContext.current should not return closed contexts
        assertSame(SpanContext.invalid, SpanContext.current)
        assertEquals(0, SpanContext.contextStack.size)
    }

    @Test
    fun runnableWrapper() {
        spanFactory.createCustomSpan("parent thread").use {
            val executorService = Executors.newSingleThreadExecutor()
            executorService.submit(
                SpanContext.current.wrap(
                    Runnable {
                        spanFactory.createCustomSpan("worker thread").end(1L)
                    }
                )
            ).get()
        }

        val collectedSpans = spanProcessor.toList()
        assertEquals(2, collectedSpans.size)
        assertEquals(collectedSpans[1].spanId, collectedSpans[0].parentSpanId)
    }

    @Test
    fun callableWrapper() {
        spanFactory.createCustomSpan("parent").use {
            val executorService = Executors.newSingleThreadExecutor()
            executorService.submit(
                SpanContext.current.wrap(
                    Callable {
                        spanFactory.createCustomSpan("child").end()
                    }
                )
            ).get()
        }

        val collectedSpans = spanProcessor.toList()
        assertEquals(2, collectedSpans.size)
        assertEquals(collectedSpans[1].spanId, collectedSpans[0].parentSpanId)
    }

    private fun createTestSpan(name: String = "Test/test span", options: SpanOptions = SpanOptions.defaults) =
        spanFactory.createCustomSpan(name, options)

    private data class TestSpanContext(
        override val spanId: Long,
        override val traceId: UUID,
        val threadId: Long
    ) : SpanContext {
        override fun wrap(runnable: Runnable) = runnable
        override fun <T> wrap(callable: Callable<T>) = callable
    }
}
