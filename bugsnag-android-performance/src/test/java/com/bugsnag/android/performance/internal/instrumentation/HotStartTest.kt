package com.bugsnag.android.performance.internal.instrumentation

import android.app.Activity
import android.os.Bundle
import com.bugsnag.android.performance.internal.AutoInstrumentationCache
import com.bugsnag.android.performance.internal.Loopers
import com.bugsnag.android.performance.internal.SpanCategory
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.SpanTracker
import com.bugsnag.android.performance.test.ActivityLifecycleHelper
import com.bugsnag.android.performance.test.ActivityLifecycleStep
import com.bugsnag.android.performance.test.CollectingSpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPausedSystemClock
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowPausedSystemClock::class], sdk = [32])
class HotStartTest {

    private lateinit var spanTracker: SpanTracker
    private lateinit var spanProcessor: CollectingSpanProcessor
    private lateinit var spanFactory: SpanFactory
    private lateinit var appStartInstrumentation: AppStartInstrumentation
    private lateinit var activityInstrumentation: ActivityCallbacks
    private lateinit var lifecycleHelper: ActivityLifecycleHelper
    private lateinit var autoInstrumentationCache: AutoInstrumentationCache

    @Before
    fun setup() {
        spanTracker = SpanTracker()
        spanProcessor = CollectingSpanProcessor()
        spanFactory = SpanFactory(spanProcessor)
        autoInstrumentationCache = AutoInstrumentationCache()
        appStartInstrumentation = AppStartInstrumentation(spanTracker, spanFactory)
        activityInstrumentation = ActivityCallbacks(
            spanTracker,
            spanFactory,
            appStartInstrumentation,
            autoInstrumentationCache,
        )
        lifecycleHelper = ActivityLifecycleHelper(activityInstrumentation) {
            ShadowPausedSystemClock.advanceBy(1, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun testWarmStart() {
        val activity = Activity()

        lifecycleHelper.progressLifecycle(
            activity,
            from = ActivityLifecycleStep.DESTROYED,
            to = ActivityLifecycleStep.RESUMED,
        )

        Shadows.shadowOf(Loopers.main).runToEndOfTasks()

        val spans = spanProcessor.toList()

        val appStartSpan = spans.find { it.category == SpanCategory.APP_START }!!
        assertEquals("[AppStart/AndroidWarm]", appStartSpan.name)
        assertEquals(0L, appStartSpan.parentSpanId)

        val viewLoad = spans.find { it.category == SpanCategory.VIEW_LOAD }!!
        assertEquals("[ViewLoad/Activity]Activity", viewLoad.name)
        assertEquals(appStartSpan.spanId, viewLoad.parentSpanId)

        val viewLoadPhases = spans.filter { it.category == SpanCategory.VIEW_LOAD_PHASE }
        assertEquals(3, viewLoadPhases.size)
        viewLoadPhases.forEach { phase ->
            assertEquals(viewLoad.spanId, phase.parentSpanId)
        }
    }

    @Test
    fun testHotStart() {
        val activity = Activity()
        val savedInstanceState = Bundle()

        activityInstrumentation.onActivityPreCreated(activity, savedInstanceState)
        activityInstrumentation.onActivityCreated(activity, savedInstanceState)
        activityInstrumentation.onActivityPostCreated(activity, savedInstanceState)

        lifecycleHelper.progressLifecycle(
            activity,
            from = ActivityLifecycleStep.CREATED,
            to = ActivityLifecycleStep.RESUMED,
        )

        Shadows.shadowOf(Loopers.main).runToEndOfTasks()

        val spans = spanProcessor.toList()

        val appStartSpan = spans.find { it.category == SpanCategory.APP_START }!!
        assertEquals("[AppStart/AndroidHot]", appStartSpan.name)
        assertEquals(0L, appStartSpan.parentSpanId)

        val viewLoad = spans.find { it.category == SpanCategory.VIEW_LOAD }!!
        assertEquals("[ViewLoad/Activity]Activity", viewLoad.name)
        assertEquals(appStartSpan.spanId, viewLoad.parentSpanId)

        val viewLoadPhases = spans.filter { it.category == SpanCategory.VIEW_LOAD_PHASE }
        assertEquals(3, viewLoadPhases.size)
        viewLoadPhases.forEach { phase ->
            assertEquals(viewLoad.spanId, phase.parentSpanId)
        }
    }
}
