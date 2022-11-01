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

class PerformancePlatformCallbacksTest {
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

        val callbacks = PerformancePlatformCallbacks(
            activityLoadSpans = spanTracker,
            spanFactory = spanFactory
        ).apply {
            openLoadSpans = true
            closeLoadSpans = true
            instrumentAppStart = false
        }

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

        val callbacks = PerformancePlatformCallbacks(
            activityLoadSpans = spanTracker,
            spanFactory = spanFactory
        ).apply {
            openLoadSpans = true
            closeLoadSpans = false
            instrumentAppStart = false
        }

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

    @Test
    fun repeatedAppStart() = withStaticMock<SystemClock> { mockedClock ->
        mockedClock.`when`<Long> { SystemClock.elapsedRealtimeNanos() }
            .thenReturn(10L) // Span1 Start
            .thenReturn(20L) // Span1 End
            .thenReturn(30L) //
            .thenReturn(40L)

        val callbacks = PerformancePlatformCallbacks(
            activityLoadSpans = spanTracker,
            spanFactory = spanFactory
        ).apply {
            openLoadSpans = false
            closeLoadSpans = false
            instrumentAppStart = true
        }

        callbacks.onActivityCreated(activity, null) // Warm start
        callbacks.onActivityStarted(activity)
        callbacks.onActivityResumed(activity)

        val activity2 = Activity()
        val activity3 = Activity()

        cycleActivity(activity2, callbacks)
        cycleActivity(activity3, callbacks)
        cycleActivity(activity2, callbacks)

        callbacks.onActivityPaused(activity)
        callbacks.onActivityStopped(activity)
        callbacks.onActivityDestroyed(activity)

        callbacks.onActivityCreated(activity, null) // Warm start
        callbacks.onActivityStarted(activity)
        callbacks.onActivityResumed(activity)

        // we expect to see 2 warm starts
        val spans = spanProcessor.toList()
        assertEquals(2, spans.size)

        val (span1, span2) = spans
        assertEquals("AppStart/Warm", span1.name)
        assertEquals(10L, span1.startTime)
        assertEquals(20L, span1.endTime)

        assertEquals("AppStart/Warm", span2.name)
        assertEquals(30L, span2.startTime)
        assertEquals(40L, span2.endTime)
    }

    private fun cycleActivity(activity: Activity, callbacks: PerformancePlatformCallbacks) {
        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity)
        callbacks.onActivityStopped(activity)
        callbacks.onActivityDestroyed(activity)
    }
}
