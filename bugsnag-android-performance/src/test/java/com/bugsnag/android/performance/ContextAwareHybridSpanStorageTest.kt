package com.bugsnag.android.performance

import com.bugsnag.android.performance.context.HybridSpanContextStorage
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.test.CollectingSpanProcessor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class ContextAwareHybridSpanStorageTest {
    private lateinit var spanFactory: SpanFactory
    private lateinit var spanProcessor: CollectingSpanProcessor
    private lateinit var storage: HybridSpanContextStorage

    @Before
    fun setup() {
        spanProcessor = CollectingSpanProcessor()
        spanFactory = SpanFactory(spanProcessor)
        storage = HybridSpanContextStorage()

        SpanContext.defaultStorage = storage
    }

    @After
    fun tearDownContextStorage() {
        SpanContext.defaultStorage = null
    }

    @Test
    fun testContextAwareDispatch() {
        val rootSpan = spanFactory.createCustomSpan("root")

        val executor =
            ContextAwareThreadPoolExecutor(
                1,
                1,
                100L,
                TimeUnit.MILLISECONDS,
                LinkedBlockingQueue(),
            )

        // submit a task while the root span is active
        executor.submit {
            repeat(9000) {
                assertNotNull(storage.localContextStack)
                spanFactory.createCustomSpan("task 1").use { taskSpan ->
                    assertEquals(rootSpan.spanId, taskSpan.parentSpanId)
                }
            }
        }.get()

        repeat(9000) {
            // submit a task while the child span is active
            spanFactory.createCustomSpan("child").use { childSpan ->
                executor.submit {
                    spanFactory.createCustomSpan("task 2").use { taskSpan ->
                        assertEquals(childSpan.spanId, taskSpan.parentSpanId)
                    }
                }.get()
            }
        }

        rootSpan.end()
        executor.shutdown()
    }
}
