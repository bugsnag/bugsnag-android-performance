package com.bugsnag.android.performance.internal

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.test.CollectingSpanProcessor
import com.bugsnag.android.performance.test.NoopSpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPausedSystemClock
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowPausedSystemClock::class])
class PerformanceLifecycleCallbacksTest {
    private val activity = Activity()

    private lateinit var spanFactory: SpanFactory
    private lateinit var spanTracker: SpanTracker
    private lateinit var startupTracker: AppStartTracker

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

        // the startupTracker is given a NoopSpanProcessor as we aren't testing AppStart tracking
        startupTracker = AppStartTracker(spanTracker, SpanFactory(NoopSpanProcessor))
    }

    @Test
    fun fullActivityTracking() {
        // this is actually the default initial value for Robolectric, but we set it manually
        // just as a form of documentation
        SystemClock.setCurrentTimeMillis(100L)

        val callbacks = PerformanceLifecycleCallbacks(
            spanTracker = spanTracker,
            spanFactory = spanFactory,
            startupTracker = startupTracker,
            inForegroundCallback = {},
        ).apply {
            openLoadSpans = true
            closeLoadSpans = true
        }

        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity) // shouldn't do anything

        SystemClock.setCurrentTimeMillis(200L)
        callbacks.onActivityResumed(activity)

        val spans = spanProcessor.toList()
        assertEquals(1, spans.size)

        val span = spans.first()
        assertEquals("[ViewLoad/Activity]Activity", span.name)
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
            startupTracker = startupTracker,
            inForegroundCallback = {},
        ).apply {
            openLoadSpans = true
            closeLoadSpans = false
        }

        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity) // shouldn't do anything
        SystemClock.setCurrentTimeMillis(200L)
        callbacks.onActivityResumed(activity) // shouldn't do anything

        assertEquals(0, spanProcessor.toList().size)

        SystemClock.setCurrentTimeMillis(300L)
        callbacks.onActivityPaused(activity)
        callbacks.onActivityStopped(activity)

        Robolectric.flushForegroundThreadScheduler()

        val spans = spanProcessor.toList()
        assertEquals(1, spans.size)

        val span = spans.first()
        assertEquals("[ViewLoad/Activity]Activity", span.name)
        assertEquals(100_000_000L, span.startTime)
        assertEquals(200_000_000L, span.endTime)
        assertEquals(SpanKind.INTERNAL, span.kind)
        assertTrue(span.isEnded())
    }

    @Test
    fun startOnlyActivityTrackingWithLeak_CreateAndDestroy() {
        // this is actually the default initial value for Robolectric, but we set it manually
        // just as a form of documentation
        SystemClock.setCurrentTimeMillis(100L)

        val callbacks = PerformanceLifecycleCallbacks(
            spanTracker = spanTracker,
            spanFactory = spanFactory,
            startupTracker = startupTracker,
            inForegroundCallback = {},
        ).apply {
            openLoadSpans = true
            closeLoadSpans = false
        }

        callbacks.onActivityCreated(activity, null)
        assertEquals(0, spanProcessor.toList().size)

        // the Activity has called finish() from onCreate
        SystemClock.setCurrentTimeMillis(300L)
        callbacks.onActivityDestroyed(activity)

        Robolectric.flushForegroundThreadScheduler()

        val spans = spanProcessor.toList()
        assertEquals(1, spans.size)

        val span = spans.first()
        assertEquals("[ViewLoad/Activity]Activity", span.name)
        assertEquals(100_000_000L, span.startTime)
        assertEquals(300_000_000L, span.endTime)
        assertEquals(SpanKind.INTERNAL, span.kind)
        assertTrue(span.isEnded())
    }

    @Test
    fun fullViewLoadPhaseTracking() {
        // this is actually the default initial value for Robolectric, but we set it manually
        // just as a form of documentation
        SystemClock.setCurrentTimeMillis(100L)

        ReflectionHelpers.setStaticField(
            Build.VERSION::class.java,
            "SDK_INT",
            Build.VERSION_CODES.Q,
        )

        val callbacks = PerformanceLifecycleCallbacks(
            spanTracker = spanTracker,
            spanFactory = spanFactory,
            startupTracker = startupTracker,
            inForegroundCallback = {},
        ).apply {
            openLoadSpans = true
            closeLoadSpans = true
        }

        cycleActivityLoad(activity, callbacks)

        val spans = spanProcessor.toList()
        assertEquals(4, spans.size)

        // we expect the view load span to be second as it was ended after the onCreate span
        val viewLoadSpan = spans[1]
        assertEquals("[ViewLoad/Activity]Activity", viewLoadSpan.name)
        assertEquals(100_000_000L, viewLoadSpan.startTime)
        assertEquals(200_000_000L, viewLoadSpan.endTime)
        assertEquals(SpanKind.INTERNAL, viewLoadSpan.kind)
        assertTrue(viewLoadSpan.isEnded())

        val onCreateSpan = spans.first()
        assertEquals("[ViewLoadPhase/ActivityCreate]Activity", onCreateSpan.name)
        assertEquals(viewLoadSpan.spanId, onCreateSpan.parentSpanId)
        assertEquals(100_000_000L, onCreateSpan.startTime)
        assertEquals(100_000_000L, onCreateSpan.endTime)
        assertEquals(SpanKind.INTERNAL, onCreateSpan.kind)
        assertTrue(onCreateSpan.isEnded())

        val onStartSpan = spans[2]
        assertEquals("[ViewLoadPhase/ActivityStart]Activity", onStartSpan.name)
        assertEquals(viewLoadSpan.spanId, onStartSpan.parentSpanId)
        assertEquals(150_000_000L, onStartSpan.startTime)
        assertEquals(150_000_000L, onStartSpan.endTime)
        assertEquals(SpanKind.INTERNAL, onStartSpan.kind)
        assertTrue(onStartSpan.isEnded())

        val onResumeSpan = spans[3]
        assertEquals("[ViewLoadPhase/ActivityResume]Activity", onResumeSpan.name)
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

        ReflectionHelpers.setStaticField(
            Build.VERSION::class.java,
            "SDK_INT",
            Build.VERSION_CODES.Q,
        )

        val callbacks = PerformanceLifecycleCallbacks(
            spanTracker = spanTracker,
            spanFactory = spanFactory,
            startupTracker = startupTracker,
            inForegroundCallback = {},
        ).apply {
            openLoadSpans = true
            closeLoadSpans = false
        }

        cycleActivityLoad(activity, callbacks)

        // at this stage the 3 view load phase spans should have been ended but
        // the parent view load span is still open
        assertEquals(3, spanProcessor.toList().size)

        // end view load span (using the leak detection)
        callbacks.onActivityStopped(activity)

        val spans = spanProcessor.toList()
        assertEquals(4, spans.size)

        // we expect the view load span to be second as it was ended after the onCreate span
        val viewLoadSpan = spans[1]
        assertEquals("[ViewLoad/Activity]Activity", viewLoadSpan.name)
        assertEquals(100_000_000L, viewLoadSpan.startTime)
        assertEquals(200_000_000L, viewLoadSpan.endTime)
        assertEquals(SpanKind.INTERNAL, viewLoadSpan.kind)
        assertTrue(viewLoadSpan.isEnded())

        val onCreateSpan = spans.first()
        assertEquals("[ViewLoadPhase/ActivityCreate]Activity", onCreateSpan.name)
        assertEquals(viewLoadSpan.spanId, onCreateSpan.parentSpanId)
        assertEquals(100_000_000L, onCreateSpan.startTime)
        assertEquals(100_000_000L, onCreateSpan.endTime)
        assertEquals(SpanKind.INTERNAL, onCreateSpan.kind)
        assertTrue(onCreateSpan.isEnded())

        val onStartSpan = spans[2]
        assertEquals("[ViewLoadPhase/ActivityStart]Activity", onStartSpan.name)
        assertEquals(viewLoadSpan.spanId, onStartSpan.parentSpanId)
        assertEquals(150_000_000L, onStartSpan.startTime)
        assertEquals(150_000_000L, onStartSpan.endTime)
        assertEquals(SpanKind.INTERNAL, onStartSpan.kind)
        assertTrue(onStartSpan.isEnded())

        val onResumeSpan = spans[3]
        assertEquals("[ViewLoadPhase/ActivityResume]Activity", onResumeSpan.name)
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
            startupTracker = startupTracker,
            inForegroundCallback = {},
        ).apply {
            openLoadSpans = false
            closeLoadSpans = false
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
