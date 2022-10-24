package com.bugsnag.android.performance.internal

import android.app.Activity
import android.os.SystemClock
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.test.CollectingSpanProcessor
import com.bugsnag.android.performance.test.testSpanProcessor
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PerformanceActivityLifecycleCallbacksTest {
    private val activity = Activity()

    private var spanFactory = SpanFactory(testSpanProcessor)
    private var spanTracker = SpanTracker<Activity>()

    private lateinit var spanProcessor: CollectingSpanProcessor

    @Before
    fun configureSafeLogger() {
        Logger.delegate = NoopLogger
    }

    @Before
    fun setupDependencies() {
        spanProcessor = CollectingSpanProcessor()
        spanFactory = SpanFactory(spanProcessor)
        spanTracker = SpanTracker()
    }

    @Test
    fun fullActivityTracking() = withStaticMock<SystemClock> { mockedClock ->
        mockedClock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
            .thenReturn(10L)
            .thenReturn(20L)

        val callbacks = PerformanceActivityLifecycleCallbacks(
            openLoadSpans = true,
            closeLoadSpans = true,
            activityLoadSpans = spanTracker,
            spanFactory = spanFactory
        )

        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity) // shouldn't do anything
        callbacks.onActivityResumed(activity)

        val spans = spanProcessor.toList()
        assertEquals(1, spans.size)

        val span = spans.first()
        assertEquals("ViewLoad/Activity/Activity", span.name)
        assertEquals(10L, span.startTime)
        assertEquals(20L, span.endTime)
        assertEquals(SpanKind.INTERNAL, span.kind)
        assertTrue(span.isNotOpen())
    }

    @Test
    fun startOnlyActivityTrackingWithLeak() = withStaticMock<SystemClock> { mockedClock ->
        mockedClock.`when`<Long> { SystemClock.elapsedRealtimeNanos() }
            .thenReturn(10L)
            .thenReturn(20L)
            .thenReturn(30L)
            .thenReturn(40L)

        val callbacks = PerformanceActivityLifecycleCallbacks(
            openLoadSpans = true,
            closeLoadSpans = false,
            activityLoadSpans = spanTracker,
            spanFactory = spanFactory
        )

        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity) // shouldn't do anything
        callbacks.onActivityResumed(activity) // shouldn't do anything

        assertEquals(0, spanProcessor.toList().size)

        callbacks.onActivityPaused(activity)
        callbacks.onActivityStopped(activity)
        callbacks.onActivityDestroyed(activity)

        val spans = spanProcessor.toList()
        assertEquals(1, spans.size)

        val span = spans.first()
        assertEquals("ViewLoad/Activity/Activity", span.name)
        assertEquals(10L, span.startTime)
        assertEquals(20L, span.endTime)
        assertEquals(SpanKind.INTERNAL, span.kind)
        assertTrue(span.isNotOpen())
    }
}
