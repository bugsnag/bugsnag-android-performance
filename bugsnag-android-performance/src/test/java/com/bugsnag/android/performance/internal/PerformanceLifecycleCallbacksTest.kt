package com.bugsnag.android.performance.internal

import android.app.Activity
import android.os.Build
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
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowPausedSystemClock::class])
class PerformanceLifecycleCallbacksTest {
    private val activity = Activity()

    private var spanFactory = SpanFactory(testSpanProcessor)
    private var spanTracker = SpanTracker()

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
            spanTracker = spanTracker,
            spanFactory = spanFactory,
            inForegroundCallback = {},
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
        assertEquals("ViewLoad/Activity/Activity", span.name)
        // start and end time are in nanoseconds, not milliseconds
        assertEquals(100_000_000L, span.startTime)
        assertEquals(200_000_000L, span.endTime)
        assertEquals(SpanKind.INTERNAL, span.kind)
        assertTrue(span.isEnded())
    }

    @Test
    fun startOnlyActivityTrackingWithLeak() {
        // this is actually the default initial value for Robolectric, but we set it manually
        // just as a form of documentation
        SystemClock.setCurrentTimeMillis(100L)

        val callbacks = PerformanceLifecycleCallbacks(
            spanTracker = spanTracker,
            spanFactory = spanFactory,
            inForegroundCallback = {},
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
        assertEquals("ViewLoad/Activity/Activity", span.name)
        assertEquals(100_000_000L, span.startTime)
        assertEquals(200_000_000L, span.endTime)
        assertEquals(SpanKind.INTERNAL, span.kind)
        assertTrue(span.isEnded())
    }

    @Test
    fun repeatedAppStart() {
        // this is actually the default initial value for Robolectric, but we set it manually
        // just as a form of documentation
        SystemClock.setCurrentTimeMillis(100L)

        val callbacks = PerformanceLifecycleCallbacks(
            spanTracker = spanTracker,
            spanFactory = spanFactory,
            inForegroundCallback = {},
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

    @Test
    fun fullViewLoadPhaseTracking() {
        // this is actually the default initial value for Robolectric, but we set it manually
        // just as a form of documentation
        SystemClock.setCurrentTimeMillis(100L)

        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.Q)

        val callbacks = PerformanceLifecycleCallbacks(
            spanTracker = spanTracker,
            spanFactory = spanFactory,
            inForegroundCallback = {},
        ).apply {
            openLoadSpans = true
            closeLoadSpans = true
            instrumentAppStart = false
        }

        cycleActivityLoad(activity, callbacks)

        val spans = spanProcessor.toList()
        assertEquals(4, spans.size)

        // we expect the view load span to be second as it was ended after the onCreate span
        val viewLoadSpan = spans[1]
        assertEquals("ViewLoad/Activity/Activity", viewLoadSpan.name)
        assertEquals(100_000_000L, viewLoadSpan.startTime)
        assertEquals(200_000_000L, viewLoadSpan.endTime)
        assertEquals(SpanKind.INTERNAL, viewLoadSpan.kind)
        assertTrue(viewLoadSpan.isEnded())

        val onCreateSpan = spans.first()
        assertEquals("ViewLoadPhase/ActivityCreate/Activity", onCreateSpan.name)
        assertEquals(viewLoadSpan.spanId, onCreateSpan.parentSpanId)
        assertEquals(100_000_000L, onCreateSpan.startTime)
        assertEquals(100_000_000L, onCreateSpan.endTime)
        assertEquals(SpanKind.INTERNAL, onCreateSpan.kind)
        assertTrue(onCreateSpan.isEnded())

        val onStartSpan = spans[2]
        assertEquals("ViewLoadPhase/ActivityStart/Activity", onStartSpan.name)
        assertEquals(viewLoadSpan.spanId, onStartSpan.parentSpanId)
        assertEquals(150_000_000L, onStartSpan.startTime)
        assertEquals(150_000_000L, onStartSpan.endTime)
        assertEquals(SpanKind.INTERNAL, onStartSpan.kind)
        assertTrue(onStartSpan.isEnded())

        val onResumeSpan = spans[3]
        assertEquals("ViewLoadPhase/ActivityResume/Activity", onResumeSpan.name)
        assertEquals(viewLoadSpan.spanId, onResumeSpan.parentSpanId)
        assertEquals(200_000_000L, onResumeSpan.startTime)
        assertEquals(200_000_000L, onResumeSpan.endTime)
        assertEquals(SpanKind.INTERNAL, onResumeSpan.kind)
        assertTrue(onResumeSpan.isEnded())
    }

    @Test
    fun startOnlyViewLoadPhaseTracking() {
        // this is actually the default initial value for Robolectric, but we set it manually
        // just as a form of documentation
        SystemClock.setCurrentTimeMillis(100L)

        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.Q)

        val callbacks = PerformanceLifecycleCallbacks(
            spanTracker = spanTracker,
            spanFactory = spanFactory,
            inForegroundCallback = {},
        ).apply {
            openLoadSpans = true
            closeLoadSpans = false
            instrumentAppStart = false
        }

        cycleActivityLoad(activity, callbacks)

        // at this stage the 3 view load phase spans should have been ended but
        // the parent view load span is still open
        assertEquals(3, spanProcessor.toList().size)

        // end view load span
        callbacks.onActivityDestroyed(activity)

        val spans = spanProcessor.toList()
        assertEquals(4, spans.size)

        // we expect the view load span to be second as it was ended after the onCreate span
        val viewLoadSpan = spans[1]
        assertEquals("ViewLoad/Activity/Activity", viewLoadSpan.name)
        assertEquals(100_000_000L, viewLoadSpan.startTime)
        assertEquals(200_000_000L, viewLoadSpan.endTime)
        assertEquals(SpanKind.INTERNAL, viewLoadSpan.kind)
        assertTrue(viewLoadSpan.isEnded())

        val onCreateSpan = spans.first()
        assertEquals("ViewLoadPhase/ActivityCreate/Activity", onCreateSpan.name)
        assertEquals(viewLoadSpan.spanId, onCreateSpan.parentSpanId)
        assertEquals(100_000_000L, onCreateSpan.startTime)
        assertEquals(100_000_000L, onCreateSpan.endTime)
        assertEquals(SpanKind.INTERNAL, onCreateSpan.kind)
        assertTrue(onCreateSpan.isEnded())

        val onStartSpan = spans[2]
        assertEquals("ViewLoadPhase/ActivityStart/Activity", onStartSpan.name)
        assertEquals(viewLoadSpan.spanId, onStartSpan.parentSpanId)
        assertEquals(150_000_000L, onStartSpan.startTime)
        assertEquals(150_000_000L, onStartSpan.endTime)
        assertEquals(SpanKind.INTERNAL, onStartSpan.kind)
        assertTrue(onStartSpan.isEnded())

        val onResumeSpan = spans[3]
        assertEquals("ViewLoadPhase/ActivityResume/Activity", onResumeSpan.name)
        assertEquals(viewLoadSpan.spanId, onResumeSpan.parentSpanId)
        assertEquals(200_000_000L, onResumeSpan.startTime)
        assertEquals(200_000_000L, onResumeSpan.endTime)
        assertEquals(SpanKind.INTERNAL, onResumeSpan.kind)
        assertTrue(onResumeSpan.isEnded())
    }

    @Test
    fun noViewLoadPhaseTracking() {
        // this is actually the default initial value for Robolectric, but we set it manually
        // just as a form of documentation
        SystemClock.setCurrentTimeMillis(100L)

        val callbacks = PerformanceLifecycleCallbacks(
            spanTracker = spanTracker,
            spanFactory = spanFactory,
            inForegroundCallback = {},
        ).apply {
            openLoadSpans = false
            closeLoadSpans = false
            instrumentAppStart = false
        }

        cycleActivityLoad(activity, callbacks)
        callbacks.onActivityDestroyed(activity)

        assertEquals(0, spanProcessor.toList().size)

        callbacks.openLoadSpans = true
        callbacks.closeLoadSpans = true

        callbacks.onActivityPreStarted(activity)
        callbacks.onActivityStarted(activity)
        callbacks.onActivityPostStarted(activity)
        callbacks.onActivityPreResumed(activity)
        callbacks.onActivityResumed(activity)
        callbacks.onActivityPostResumed(activity)

        // there is no parent view load span so we expect no spans for the view load phases
        assertEquals(0, spanProcessor.toList().size)
    }

    private fun cycleActivity(activity: Activity, callbacks: PerformanceLifecycleCallbacks) {
        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity)
        callbacks.onActivityStopped(activity)
        callbacks.onActivityDestroyed(activity)
    }

    private fun cycleActivityLoad(activity: Activity, callbacks: PerformanceLifecycleCallbacks) {
        callbacks.onActivityPreCreated(activity, null)
        callbacks.onActivityCreated(activity, null) // shouldn't do anything
        callbacks.onActivityPostCreated(activity, null)

        SystemClock.setCurrentTimeMillis(150L)

        callbacks.onActivityPreStarted(activity)
        callbacks.onActivityStarted(activity) // shouldn't do anything
        callbacks.onActivityPostStarted(activity)

        SystemClock.setCurrentTimeMillis(200L)

        callbacks.onActivityPreResumed(activity)
        callbacks.onActivityResumed(activity) // shouldn't do anything
        callbacks.onActivityPostResumed(activity)
    }
}
