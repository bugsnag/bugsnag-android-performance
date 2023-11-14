package com.bugsnag.android.performance.internal.instrumentation

import android.app.Activity
import com.bugsnag.android.performance.AutoInstrumentationCache
import com.bugsnag.android.performance.DoNotAutoInstrument
import com.bugsnag.android.performance.DoNotEndAppStart
import com.bugsnag.android.performance.internal.Loopers
import com.bugsnag.android.performance.internal.SpanCategory
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.SpanTracker
import com.bugsnag.android.performance.test.ActivityLifecycleHelper
import com.bugsnag.android.performance.test.ActivityLifecycleStep.CREATED
import com.bugsnag.android.performance.test.ActivityLifecycleStep.DESTROYED
import com.bugsnag.android.performance.test.ActivityLifecycleStep.RESUMED
import com.bugsnag.android.performance.test.CollectingSpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPausedSystemClock
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowPausedSystemClock::class])
class LegacyActivityInstrumentationTest {

    private lateinit var spanTracker: SpanTracker
    private lateinit var spanProcessor: CollectingSpanProcessor
    private lateinit var spanFactory: SpanFactory
    private lateinit var activityInstrumentation: LegacyActivityInstrumentation
    private lateinit var autoInstrumentationCache: AutoInstrumentationCache

    @Before
    fun setup() {
        spanTracker = SpanTracker()
        spanProcessor = CollectingSpanProcessor()
        spanFactory = SpanFactory(spanProcessor)
        autoInstrumentationCache = AutoInstrumentationCache()
        activityInstrumentation = LegacyActivityInstrumentation(
            spanTracker,
            spanFactory,
            mock(),
            autoInstrumentationCache,
        )
    }

    @Test
    fun defaultActivityInstrumentation() {
        val activity = Activity()

        val lifecycleHelper = ActivityLifecycleHelper(activityInstrumentation) {
            ShadowPausedSystemClock.advanceBy(1, TimeUnit.MILLISECONDS)
        }

        lifecycleHelper.progressLifecycle(activity, from = DESTROYED, to = RESUMED)

        Shadows.shadowOf(Loopers.main).runToEndOfTasks()

        val spans = spanProcessor.toList()
        assertEquals(1, spans.size)

        val viewLoad = spans[0]
        assertEquals("[ViewLoad/Activity]Activity", viewLoad.name)
        assertEquals(SpanCategory.VIEW_LOAD, viewLoad.category)
        assertEquals(100_000_000L, viewLoad.startTime)
        assertEquals(102_000_000L, viewLoad.endTime)
    }

    @Test
    fun disableActivityInstrumentation() {
        @DoNotAutoInstrument
        class DoNotAutoInstrumentActivity : Activity()

        val activity = DoNotAutoInstrumentActivity()

        val lifecycleHelper = ActivityLifecycleHelper(activityInstrumentation) {
            ShadowPausedSystemClock.advanceBy(1, TimeUnit.MILLISECONDS)
        }
        lifecycleHelper.progressLifecycle(activity, from = DESTROYED, to = CREATED)
        Shadows.shadowOf(Loopers.main).runToEndOfTasks()
        val spans = spanProcessor.toList()
        assertEquals(0, spans.size)
    }

    @Test
    fun activityFinishesOnCreate() {
        val activity = mock<Activity> {
            whenever(it.isFinishing) doReturn true
        }

        activityInstrumentation.onActivityCreated(activity, null)
        ShadowPausedSystemClock.advanceBy(1, TimeUnit.MILLISECONDS)

        // the activity never starts/stops and isn't destroyed

        Shadows.shadowOf(Loopers.main).runToEndOfTasks()

        val spans = spanProcessor.toList()
        assertEquals(1, spans.size)

        val viewLoad = spans[0]
        assertEquals("[ViewLoad/Activity]Activity", viewLoad.name)
        assertEquals(SpanCategory.VIEW_LOAD, viewLoad.category)
        assertEquals(100_000_000L, viewLoad.startTime)
        assertEquals(101_000_000L, viewLoad.endTime)
    }

    @Test
    fun doNotAppStartActivityFinishesOnCreate() {
        @DoNotEndAppStart
        class DoNotEndAppStartActivity : Activity() {
            override fun isFinishing() = true
        }

        val activity = DoNotEndAppStartActivity()

        val lifecycleHelper = ActivityLifecycleHelper(activityInstrumentation) {
            ShadowPausedSystemClock.advanceBy(1, TimeUnit.MILLISECONDS)
        }

        lifecycleHelper.progressLifecycle(activity, from = DESTROYED, to = CREATED)
        Shadows.shadowOf(Loopers.main).runToEndOfTasks()
        val spans = spanProcessor.toList()
        assertEquals(1, spans.size)
    }

    @Test
    fun leakedViewLoad() {
        val activity = Activity()

        // we tell the Instrumentation not to auto-close the span - but we also don't close it manually
        activityInstrumentation.closeLoadSpans = false

        val lifecycleHelper = ActivityLifecycleHelper(activityInstrumentation) {
            ShadowPausedSystemClock.advanceBy(1, TimeUnit.MILLISECONDS)
        }

        lifecycleHelper.progressLifecycle(activity, from = DESTROYED, to = RESUMED)
        // tear the activity back down again
        lifecycleHelper.progressLifecycle(activity, from = RESUMED, to = DESTROYED)

        Shadows.shadowOf(Loopers.main).runToEndOfTasks()

        val spans = spanProcessor.toList()
        assertEquals(1, spans.size)

        val viewLoad = spans[0]
        assertEquals("[ViewLoad/Activity]Activity", viewLoad.name)
        assertEquals(SpanCategory.VIEW_LOAD, viewLoad.category)
        assertEquals(100_000_000L, viewLoad.startTime)
        assertEquals(102_000_000L, viewLoad.endTime)
    }
}
