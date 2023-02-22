package com.bugsnag.android.performance

import android.os.SystemClock
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.test.CollectingSpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPausedSystemClock
import java.lang.Thread.sleep
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowPausedSystemClock::class])
class ContextAwareScheduledThreadPoolExecutorTest {
    private lateinit var spanFactory: SpanFactory
    private lateinit var spanProcessor: CollectingSpanProcessor

    @Before
    fun newSpanFactory() {
        spanProcessor = CollectingSpanProcessor()
        spanFactory = SpanFactory(spanProcessor)
    }

    @Test
    fun execute() {
        val scheduledExecutor = ContextAwareScheduledThreadPoolExecutor(1)
        spanFactory.createCustomSpan("parent").use {
            scheduledExecutor.execute {
                spanFactory.createCustomSpan("child").end(1L)
            }
            scheduledExecutor.shutdown()
            scheduledExecutor.awaitTermination(50L, TimeUnit.MILLISECONDS)
        }

        val collectedSpans = spanProcessor.toList()
        assertSame(2, collectedSpans.size)
        assertEquals(collectedSpans[1].spanId, collectedSpans[0].parentSpanId)
    }

    @Test
    fun scheduleRunnable() {
        val scheduledExecutor = ContextAwareScheduledThreadPoolExecutor(1)
        spanFactory.createCustomSpan("parent").use { rootSpan ->
            scheduledExecutor.schedule(
                {
                    spanFactory.createCustomSpan("child").use { taskSpan ->
                        assertEquals(rootSpan.spanId, taskSpan.parentSpanId)
                    }
                },
                0L,
                TimeUnit.MILLISECONDS
            ).get()
        }
    }

    @Test
    fun scheduleCallable() {
        val scheduledExecutor = ContextAwareScheduledThreadPoolExecutor(1)
        spanFactory.createCustomSpan("parent").use { rootSpan ->
            scheduledExecutor.schedule(
                Callable {
                    spanFactory.createCustomSpan("child").use { taskSpan ->
                        assertEquals(rootSpan.spanId, taskSpan.parentSpanId)
                    }
                },
                0L,
                TimeUnit.MILLISECONDS
            ).get()
        }
    }

    @Test
    fun scheduleWithFixedDelay() {
        val scheduledExecutor = ContextAwareScheduledThreadPoolExecutor(1)
        var startTime = SystemClock.elapsedRealtime()
        spanFactory.createCustomSpan("parent").use {
            scheduledExecutor.scheduleWithFixedDelay(
                {
                    startTime += 100L
                    SystemClock.setCurrentTimeMillis(startTime)
                    spanFactory.createCustomSpan("child").end()
                },
                0L,
                100L,
                TimeUnit.MILLISECONDS
            )

            // end the parent span after one task execution
            sleep(75L)
        }

        // allow task to run once more
        sleep(75L)
        scheduledExecutor.shutdownNow()

        val collectedSpans = spanProcessor.toList()
        assertSame(3, collectedSpans.size)
        assertEquals(collectedSpans[0].spanId, collectedSpans[1].parentSpanId)
        assertEquals(0, collectedSpans[2].parentSpanId)
    }

    @Test
    fun scheduleAtFixedRate() {
        val scheduledExecutor = ContextAwareScheduledThreadPoolExecutor(1)
        var startTime = SystemClock.elapsedRealtime()
        spanFactory.createCustomSpan("parent").use {
            scheduledExecutor.scheduleAtFixedRate(
                {
                    startTime += 100L
                    SystemClock.setCurrentTimeMillis(startTime)
                    spanFactory.createCustomSpan("child").end()
                },
                0L,
                100L,
                TimeUnit.MILLISECONDS
            )

            // end the parent span after one task execution
            sleep(75L)
        }

        // allow task to run once more
        sleep(75L)
        scheduledExecutor.shutdownNow()

        val collectedSpans = spanProcessor.toList()
        assertSame(3, collectedSpans.size)
        assertEquals(collectedSpans[0].spanId, collectedSpans[1].parentSpanId)
        assertEquals(0, collectedSpans[2].parentSpanId)
    }
}
