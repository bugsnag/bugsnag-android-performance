package com.bugsnag.android.performance.internal.instrumentation

import android.app.Activity
import com.bugsnag.android.performance.AutoInstrumentationCache
import com.bugsnag.android.performance.internal.Loopers
import com.bugsnag.android.performance.internal.SpanCategory
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.SpanImpl
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
@Config(shadows = [ShadowPausedSystemClock::class], sdk = [32])
class ActivityLifecycleInstrumentationTest {

    private lateinit var spanTracker: SpanTracker
    private lateinit var spanProcessor: CollectingSpanProcessor
    private lateinit var spanFactory: SpanFactory
    private lateinit var activityInstrumentation: ActivityLifecycleInstrumentation
    private lateinit var lifecycleHelper: ActivityLifecycleHelper
    private lateinit var autoInstrumentationCache: AutoInstrumentationCache

    @Before
    fun setup() {
        spanTracker = SpanTracker()
        spanProcessor = CollectingSpanProcessor()
        spanFactory = SpanFactory(spanProcessor)
        autoInstrumentationCache = AutoInstrumentationCache()
        activityInstrumentation = ActivityLifecycleInstrumentation(
            spanTracker,
            spanFactory,
            mock(),
            autoInstrumentationCache,
        )
        lifecycleHelper = ActivityLifecycleHelper(activityInstrumentation) {
            ShadowPausedSystemClock.advanceBy(1, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun defaultActivityInstrumentation() {
        val activity = Activity()

        lifecycleHelper.progressLifecycle(
            activity,
            from = DESTROYED,
            to = RESUMED,
        )

        Shadows.shadowOf(Loopers.main).runToEndOfTasks()

        val spans = spanProcessor.toList()
        assertViewLoadSpans(spans)
    }

    @Test
    fun activityFinishesOnCreate() {
        val activity = mock<Activity> {
            whenever(it.isFinishing) doReturn true
        }

        lifecycleHelper.progressLifecycle(activity, from = DESTROYED, to = CREATED)
        activityInstrumentation.onActivityPreCreated(activity, null)

        // the activity never starts/stops and isn't destroyed
        Shadows.shadowOf(Loopers.main).runToEndOfTasks()

        val spans = spanProcessor.toList()
        assertEquals(2, spans.size)

        val (create, viewLoad) = spans
        assertEquals("[ViewLoad/Activity]Activity", viewLoad.name)
        assertEquals(SpanCategory.VIEW_LOAD, viewLoad.category)
        assertEquals(100_000_000L, viewLoad.startTime)
        assertEquals(101_000_000L, viewLoad.endTime)

        assertEquals("[ViewLoadPhase/ActivityCreate]Activity", create.name)
        assertEquals(SpanCategory.VIEW_LOAD_PHASE, create.category)
        assertEquals(100_000_000L, create.startTime)
        assertEquals(100_000_000L, create.endTime)
    }

    @Test
    fun leakedViewLoad() {
        val activity = Activity()

        // we tell the Instrumentation not to auto-close the span - but we also don't close it manually
        activityInstrumentation.closeLoadSpans = false

        lifecycleHelper.progressLifecycle(
            activity,
            from = DESTROYED,
            to = RESUMED,
        )

        ShadowPausedSystemClock.advanceBy(1, TimeUnit.MILLISECONDS)

        // tear the activity back down again
        lifecycleHelper.progressLifecycle(
            activity,
            from = RESUMED,
            to = DESTROYED,
        )

        Shadows.shadowOf(Loopers.main).runToEndOfTasks()

        val spans = spanProcessor.toList()
        assertViewLoadSpans(spans)
    }

    private fun assertViewLoadSpans(spans: List<SpanImpl>) {
        assertEquals(4, spans.size)

        val (create, viewLoad, start, resume) = spans
        assertEquals("[ViewLoad/Activity]Activity", viewLoad.name)
        assertEquals(SpanCategory.VIEW_LOAD, viewLoad.category)
        assertEquals(100_000_000L, viewLoad.startTime)
        assertEquals(102_000_000L, viewLoad.endTime)

        assertEquals("[ViewLoadPhase/ActivityCreate]Activity", create.name)
        assertEquals(SpanCategory.VIEW_LOAD_PHASE, create.category)
        assertEquals(100_000_000L, create.startTime)
        assertEquals(100_000_000L, create.endTime)

        assertEquals("[ViewLoadPhase/ActivityStart]Activity", start.name)
        assertEquals(SpanCategory.VIEW_LOAD_PHASE, start.category)
        assertEquals(101_000_000L, start.startTime)
        assertEquals(101_000_000L, start.endTime)

        assertEquals("[ViewLoadPhase/ActivityResume]Activity", resume.name)
        assertEquals(SpanCategory.VIEW_LOAD_PHASE, resume.category)
        assertEquals(102_000_000L, resume.startTime)
        assertEquals(102_000_000L, resume.endTime)
    }
}
