package com.bugsnag.android.performance.internal

import android.app.Activity
import android.os.SystemClock
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.test.CollectingSpanProcessor
import com.bugsnag.android.performance.test.testSpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPausedSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowPausedSystemClock::class])
class PerformanceLifecycleCallbacksTest {
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
    fun fullActivityTracking() {
        // this is actually the default initial value for Robolectric, but we set it manually
        // just as a form of documentation
        SystemClock.setCurrentTimeMillis(100L)

        val callbacks = PerformanceLifecycleCallbacks(
            activityLoadSpans = spanTracker,
            spanFactory = spanFactory,
            inForegroundCallback = {}
        ).apply {
            openLoadSpans = true
            closeLoadSpans = true
            instrumentAppStart = false
        }

        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity) // shouldn't do anything

        SystemClock.setCurrentTimeMillis(200L)
        callbacks.onActivityResumed(activity)

        val spans = spanProcessor.toList()
        assertEquals(1, spans.size)

        val span = spans.first()
        assertEquals("ViewLoaded/Activity/Activity", span.name)
        // start and end time are in nanoseconds, not milliseconds
        assertEquals(100_000_000L, span.startTime)
        assertEquals(200_000_000L, span.endTime)
        assertEquals(SpanKind.INTERNAL, span.kind)
        assertTrue(span.isNotOpen())
    }

    @Test
    fun startOnlyActivityTrackingWithLeak() {
        // this is actually the default initial value for Robolectric, but we set it manually
        // just as a form of documentation
        SystemClock.setCurrentTimeMillis(100L)

        val callbacks = PerformanceLifecycleCallbacks(
            activityLoadSpans = spanTracker,
            spanFactory = spanFactory,
            inForegroundCallback = {}
        ).apply {
            openLoadSpans = true
            closeLoadSpans = false
            instrumentAppStart = false
        }

        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity) // shouldn't do anything
        SystemClock.setCurrentTimeMillis(200L)
        callbacks.onActivityResumed(activity) // shouldn't do anything

        assertEquals(0, spanProcessor.toList().size)

        SystemClock.setCurrentTimeMillis(300L)
        callbacks.onActivityPaused(activity)
        callbacks.onActivityStopped(activity)
        SystemClock.setCurrentTimeMillis(400L)
        callbacks.onActivityDestroyed(activity)

        val spans = spanProcessor.toList()
        assertEquals(1, spans.size)

        val span = spans.first()
        assertEquals("ViewLoaded/Activity/Activity", span.name)
        assertEquals(100_000_000L, span.startTime)
        assertEquals(200_000_000L, span.endTime)
        assertEquals(SpanKind.INTERNAL, span.kind)
        assertTrue(span.isNotOpen())
    }

    @Test
    fun repeatedAppStart() {
        // this is actually the default initial value for Robolectric, but we set it manually
        // just as a form of documentation
        SystemClock.setCurrentTimeMillis(100L)

        val callbacks = PerformanceLifecycleCallbacks(
            activityLoadSpans = spanTracker,
            spanFactory = spanFactory,
            inForegroundCallback = {}
        ).apply {
            openLoadSpans = false
            closeLoadSpans = false
            instrumentAppStart = true
        }

        callbacks.onActivityCreated(activity, null) // Warm start
        callbacks.onActivityStarted(activity)
        SystemClock.setCurrentTimeMillis(200L)
        callbacks.onActivityResumed(activity)

        val activity2 = Activity()
        val activity3 = Activity()

        cycleActivity(activity2, callbacks)
        cycleActivity(activity3, callbacks)
        cycleActivity(activity2, callbacks)

        SystemClock.setCurrentTimeMillis(300L)
        callbacks.onActivityPaused(activity)
        callbacks.onActivityStopped(activity)
        callbacks.onActivityDestroyed(activity)

        callbacks.onActivityCreated(activity, null) // Warm start
        callbacks.onActivityStarted(activity)
        SystemClock.setCurrentTimeMillis(400L)
        callbacks.onActivityResumed(activity)

        // we expect to see 2 warm starts
        val spans = spanProcessor.toList()
        assertEquals(2, spans.size)

        val (span1, span2) = spans
        assertEquals("AppStart/Warm", span1.name)
        assertEquals(100_000_000L, span1.startTime)
        assertEquals(200_000_000L, span1.endTime)

        assertEquals("AppStart/Warm", span2.name)
        assertEquals(300_000_000L, span2.startTime)
        assertEquals(400_000_000L, span2.endTime)
    }

    private fun cycleActivity(activity: Activity, callbacks: PerformanceLifecycleCallbacks) {
        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity)
        callbacks.onActivityStopped(activity)
        callbacks.onActivityDestroyed(activity)
    }
}
