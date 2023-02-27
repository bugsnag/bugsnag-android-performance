package com.bugsnag.android.performance

import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.test.CollectingSpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.Thread.sleep
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class ContextAwareThreadPoolExecutorTest {
    private lateinit var spanFactory: SpanFactory
    private lateinit var spanProcessor: CollectingSpanProcessor

    @Before
    fun newSpanFactory() {
        spanProcessor = CollectingSpanProcessor()
        spanFactory = SpanFactory(spanProcessor)
    }

    @Test
    fun nestedSpans() {
        spanFactory.createCustomSpan("root").use { rootSpan ->
            val executor = ContextAwareThreadPoolExecutor(1, 1, 1L, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
            // submit a task while the root span is active
            executor.submit {
                spanFactory.createCustomSpan("task 1").use { taskSpan ->
                    assertEquals(rootSpan.spanId, taskSpan.parentSpanId)
                }
            }.get()
            // submit a task while the child span is active
            spanFactory.createCustomSpan("child").use { childSpan ->
                executor.submit {
                    spanFactory.createCustomSpan("task 2").use { taskSpan ->
                        assertEquals(childSpan.spanId, taskSpan.parentSpanId)
                    }
                }.get()
            }
        }
    }

    @Test
    fun closedContext() {
        val executor = ContextAwareThreadPoolExecutor(1, 1, 1L, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
        spanFactory.createCustomSpan("parent").use {
            executor.submit {
                // allow the parent span to close before starting a span within the task
                sleep(10L)
                spanFactory.createCustomSpan("child").end(10L)
            }
        }

        // wait for the task to complete
        executor.awaitTermination(20L, TimeUnit.MILLISECONDS)

        val collectedSpans = spanProcessor.toList()
        assertEquals(2, collectedSpans.size)
        assertEquals(0, collectedSpans[0].parentSpanId)
    }
}
