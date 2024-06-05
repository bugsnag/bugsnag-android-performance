package com.bugsnag.android.performance.internal

import android.os.SystemClock
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.test.CollectingSpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPausedSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowPausedSystemClock::class])
class AppStartTest {
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
        startupTracker = AppStartTracker(spanTracker, spanFactory)
    }

    @Test
    fun testColdStart() {
        coldStart()

        val spans = spanProcessor.toList()
        assertEquals(
            "expected 2 spans, but was ${spans.size} ${spans.joinToString(transform = { it.name })}",
            2,
            spans.size,
        )

        val (frameworkStart, appStart) = spans
        assertEquals("[AppStartPhase/Framework]", frameworkStart.name)
        // start and end time are in nanoseconds, not milliseconds
        assertEquals(100_000_000L, frameworkStart.startTime)
        assertEquals(200_000_000L, frameworkStart.endTime.get())
        assertEquals(SpanKind.INTERNAL, frameworkStart.kind)
        assertTrue(frameworkStart.isEnded())

        assertEquals("[AppStart/AndroidCold]", appStart.name)
        // start and end time are in nanoseconds, not milliseconds
        assertEquals(100_000_000L, appStart.startTime)
        assertEquals(400_000_000L, appStart.endTime.get())
        assertEquals(SpanKind.INTERNAL, appStart.kind)
        assertTrue(appStart.isEnded())
    }

    @Test
    fun testWarmStart() {
        coldStart()

        // discard the ColdStart spans
        spanProcessor.clear()

        startupTracker.isInBackground = true

        SystemClock.setCurrentTimeMillis(500L)
        startupTracker.onActivityCreate(false)
        SystemClock.setCurrentTimeMillis(600L)
        startupTracker.onViewLoadComplete(SystemClock.elapsedRealtimeNanos())

        val spans = spanProcessor.toList()
        assertEquals(1, spans.size)

        val appStart = spans.first()
        assertEquals("[AppStart/AndroidWarm]", appStart.name)
        // start and end time are in nanoseconds, not milliseconds
        assertEquals(500_000_000L, appStart.startTime)
        assertEquals(600_000_000L, appStart.endTime.get())
        assertEquals(SpanKind.INTERNAL, appStart.kind)
        assertTrue(appStart.isEnded())
    }

    @Test
    fun backgroundStart() {
        SystemClock.setCurrentTimeMillis(100L)
        startupTracker.onFirstClassLoadReported()
        SystemClock.setCurrentTimeMillis(200L)
        startupTracker.onApplicationCreate()
        startupTracker.onBugsnagPerformanceStart()

        // schedule discard AppStart
        Shadows.shadowOf(Loopers.main).runToEndOfTasks()
        // discard AppStart:
        Shadows.shadowOf(Loopers.main).runToEndOfTasks()

        assertEquals(SpanContext.invalid, SpanContext.current)

        val spans = spanProcessor.toList()
        assertEquals(1, spans.size)

        // we will still produce a Framework start span
        val frameworkStart = spans.first()
        assertEquals("[AppStartPhase/Framework]", frameworkStart.name)
        // start and end time are in nanoseconds, not milliseconds
        assertEquals(100_000_000L, frameworkStart.startTime)
        assertEquals(200_000_000L, frameworkStart.endTime.get())
        assertEquals(SpanKind.INTERNAL, frameworkStart.kind)
        assertTrue(frameworkStart.isEnded())
    }

    private fun coldStart() {
        SystemClock.setCurrentTimeMillis(100L)
        startupTracker.onFirstClassLoadReported()
        SystemClock.setCurrentTimeMillis(200L)
        startupTracker.onApplicationCreate()
        startupTracker.onBugsnagPerformanceStart()
        // Application.onCreate completes

        Shadows.shadowOf(Loopers.main).runToEndOfTasks()

        SystemClock.setCurrentTimeMillis(300L)
        startupTracker.onActivityCreate(false)
        SystemClock.setCurrentTimeMillis(400L)
        startupTracker.onViewLoadComplete(SystemClock.elapsedRealtimeNanos())

        Shadows.shadowOf(Loopers.main).runToEndOfTasks()
    }
}
