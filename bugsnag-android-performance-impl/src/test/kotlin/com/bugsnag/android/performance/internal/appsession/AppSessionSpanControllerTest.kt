package com.bugsnag.android.performance.internal.appsession

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bugsnag.android.performance.AppSession
import com.bugsnag.android.performance.AppSessionCallback
import com.bugsnag.android.performance.AppSessionConfig
import com.bugsnag.android.performance.CloseReason
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanProcessor
import com.bugsnag.android.performance.internal.instrumentation.ForegroundState
import com.bugsnag.android.performance.internal.isInForeground
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ConcurrentLinkedQueue

@RunWith(RobolectricTestRunner::class)
class AppSessionSpanControllerTest {
    private class CollectingSpanProcessor : SpanProcessor {
        private val spans = ConcurrentLinkedQueue<SpanImpl>()

        fun toList(): List<SpanImpl> = spans.sortedBy { it.startTime }

        override fun onEnd(span: com.bugsnag.android.performance.Span) {
            spans.add(span as SpanImpl)
        }
    }

    private data class SessionEvent(
        val type: String,
        val session: AppSession,
    )

    private class RecordingSessionCallback : AppSessionCallback {
        val events = mutableListOf<SessionEvent>()

        override fun onSessionStarted(session: AppSession) {
            events.add(SessionEvent("started", session))
        }

        override fun onSessionBackgrounded(session: AppSession) {
            events.add(SessionEvent("backgrounded", session))
        }

        override fun onSessionForegrounded(session: AppSession) {
            events.add(SessionEvent("foregrounded", session))
        }

        override fun onSessionEnded(session: AppSession) {
            events.add(SessionEvent("ended", session))
        }
    }

    private lateinit var spanFactory: SpanFactory
    private lateinit var spanProcessor: CollectingSpanProcessor
    private lateinit var context: Context

    @Before
    fun setup() {
        spanProcessor = CollectingSpanProcessor()
        spanFactory =
            SpanFactory(
                spanProcessor,
                { span ->
                    span.attributes["bugsnag.app.in_foreground"] = ForegroundState.isInForeground
                },
            )
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testAutomaticSessionStartInForeground() {
        ForegroundState.isInForeground = true
        val config = AppSessionConfig(autoStartSession = true)
        val controller = AppSessionSpanController(context, spanFactory, sessionConfig = config)

        controller.endAppSessionSpan()
        val endedSpans = spanProcessor.toList()
        assertEquals(1, endedSpans.size)
        assertEquals("[AppSession/foreground]", endedSpans[0].name)
        assertTrue(endedSpans[0].attributes["bugsnag.app.in_foreground"] as Boolean)
    }

    @Test
    fun testAutomaticSessionStartInBackground() {
        ForegroundState.isInForeground = false
        val config = AppSessionConfig(autoStartSession = true)
        val foregroundTrackerClass =
            Class.forName("com.bugsnag.android.performance.internal.ForegroundTrackerKt")

        @Suppress("UNCHECKED_CAST")
        mockStatic(foregroundTrackerClass as Class<Any>).use { mockedForegroundTracker ->
            mockedForegroundTracker.`when`<Boolean> { isInForeground(null) }.thenReturn(false)

            AppSessionSpanController(context, spanFactory, sessionConfig = config).endAppSessionSpan()
        }

        val endedSpans = spanProcessor.toList()
        assertEquals(1, endedSpans.size)
        assertEquals("[AppSession/background]", endedSpans[0].name)
        assertFalse(endedSpans[0].attributes["bugsnag.app.in_foreground"] as Boolean)
    }

    @Test
    fun testAutomaticTransition() {
        ForegroundState.isInForeground = true
        val config = AppSessionConfig(autoStartSession = true)
        AppSessionSpanController(context, spanFactory, sessionConfig = config)

        // Transition to background
        ForegroundState.isInForeground = false

        val spansAfterBackground = spanProcessor.toList()
        assertEquals(1, spansAfterBackground.size)
        assertEquals("[AppSession/foreground]", spansAfterBackground[0].name)
        assertEquals("segment_switched", spansAfterBackground[0].attributes["bugsnag.session.close_reason"])

        // Transition back to foreground
        ForegroundState.isInForeground = true
        val spansAfterForeground = spanProcessor.toList()
        assertEquals(2, spansAfterForeground.size)
        assertEquals("[AppSession/background]", spansAfterForeground[1].name)
        assertEquals("segment_switched", spansAfterForeground[1].attributes["bugsnag.session.close_reason"])
    }

    @Test
    fun testManualStartUsesAutomaticDetection() {
        val config = AppSessionConfig(autoStartSession = false)
        val controller = AppSessionSpanController(context, spanFactory, sessionConfig = config)

        ForegroundState.isInForeground = true
        controller.startAppSessionSpan()
        controller.endAppSessionSpan()

        val spans = spanProcessor.toList()
        assertEquals(1, spans.size)
        assertEquals("[AppSession/foreground]", spans[0].name)

        ForegroundState.isInForeground = false
        controller.startAppSessionSpan()
        controller.endAppSessionSpan()

        val spans2 = spanProcessor.toList()
        assertEquals(2, spans2.size)
        assertEquals("[AppSession/background]", spans2[1].name)
    }

    @Test
    fun testCategoryIsAppSession() {
        ForegroundState.isInForeground = true
        val config = AppSessionConfig(autoStartSession = true)
        val controller = AppSessionSpanController(context, spanFactory, sessionConfig = config)

        controller.endAppSessionSpan()
        val endedSpans = spanProcessor.toList()
        assertEquals(1, endedSpans.size)
        // Check that the category is APP_SESSION
        assertEquals("app_session", endedSpans[0].category.category)
    }

    @Test
    fun testAppSessionDataSerialization() {
        val data =
            AppSessionData(
                sessionId = "test-session",
                index = 1,
                appSessionName = "test-name",
                startTimeMs = 1000,
                endTimeMs = 2000,
                durationMs = 1000,
                closeReason = "test-reason",
                runtimeMemoryCount = 3,
                runtimeMemoryMinBytes = 100L,
                runtimeMemoryMaxBytes = 300L,
                runtimeMemoryMeanBytes = 200L,
            )

        val json = data.toJson()
        assertEquals("test-session", json.getString("sessionId"))
        assertEquals(1, json.getInt("index"))
        assertEquals("test-name", json.getString("appSessionName"))
        assertEquals(3, json.getInt("runtimeMemoryCount"))
        assertEquals(100L, json.getLong("runtimeMemoryMinBytes"))
        assertEquals(300L, json.getLong("runtimeMemoryMaxBytes"))
        assertEquals(200L, json.getLong("runtimeMemoryMeanBytes"))
        assertEquals(3, json.getInt("artMemoryCount"))
        assertEquals(100L, json.getLong("artMemoryMinBytes"))
        assertEquals(300L, json.getLong("artMemoryMaxBytes"))
        assertEquals(200L, json.getLong("artMemoryMeanBytes"))

        val fromJson = AppSessionData.fromJson(json)
        assertEquals("test-session", fromJson.sessionId)
        assertEquals(1, fromJson.index)
        assertEquals("test-name", fromJson.appSessionName)
        assertEquals(3, fromJson.runtimeMemoryCount)
        assertEquals(100L, fromJson.runtimeMemoryMinBytes)
        assertEquals(300L, fromJson.runtimeMemoryMaxBytes)
        assertEquals(200L, fromJson.runtimeMemoryMeanBytes)
        assertEquals(3, fromJson.artMemoryCount)
        assertEquals(100L, fromJson.artMemoryMinBytes)
        assertEquals(300L, fromJson.artMemoryMaxBytes)
        assertEquals(200L, fromJson.artMemoryMeanBytes)
    }

    @Test
    fun testAppSessionMetricsUseCanonicalSystemKeys() {
        val span = spanFactory.createAppSessionSpan("App Session")
        val metrics =
            AppSessionMetrics(
                cpuCount = 2,
                cpuMin = 10.0,
                cpuMax = 20.0,
                cpuMean = 15.0,
                cpuSamples = doubleArrayOf(10.0, 20.0),
                cpuMainThreadSamples = doubleArrayOf(3.0, 4.0),
                cpuOverheadSamples = doubleArrayOf(1.0, 2.0),
                cpuMainThreadMin = 3.0,
                cpuMainThreadMax = 4.0,
                cpuMainThreadMean = 3.5,
                cpuOverheadMin = 1.0,
                cpuOverheadMax = 2.0,
                cpuOverheadMean = 1.5,
                cpuTimestamps = longArrayOf(1L, 2L),
                runtimeMemoryCount = 2,
                runtimeMemoryMinBytes = 100L,
                runtimeMemoryMaxBytes = 200L,
                runtimeMemoryMeanBytes = 150L,
                runtimeMemorySamplesBytes = longArrayOf(100L, 200L),
                runtimeMemoryTimestamps = longArrayOf(1L, 2L),
                deviceMemoryCount = 2,
                deviceMemoryMinBytes = 300L,
                deviceMemoryMaxBytes = 400L,
                deviceMemoryMeanBytes = 350L,
                deviceMemorySamplesBytes = longArrayOf(300L, 400L),
                deviceMemoryTimestamps = longArrayOf(1L, 2L),
                deviceMemorySizeBytes = 4096L,
            )

        span.attachAppSessionCpuMetrics(metrics)
        span.attachAppSessionMemoryMetrics(metrics)

        assertEquals(15.0, span.attributes["bugsnag.system.cpu_mean_total"] as Double, 0.0)
        assertEquals(10.0, span.attributes["bugsnag.system.cpu_min_total"] as Double, 0.0)
        assertEquals(20.0, span.attributes["bugsnag.system.cpu_max_total"] as Double, 0.0)
        assertArrayEquals(doubleArrayOf(10.0, 20.0), span.attributes["bugsnag.system.cpu_measures_total"] as DoubleArray, 0.0)
        assertArrayEquals(doubleArrayOf(3.0, 4.0), span.attributes["bugsnag.system.cpu_measures_main_thread"] as DoubleArray, 0.0)
        assertArrayEquals(doubleArrayOf(1.0, 2.0), span.attributes["bugsnag.system.cpu_measures_overhead"] as DoubleArray, 0.0)
        assertArrayEquals(longArrayOf(1L, 2L), span.attributes["bugsnag.system.cpu_measures_timestamps"] as LongArray)

        assertEquals(4096L, span.attributes["bugsnag.device.physical_device_memory"] as Long)
        assertEquals(4096L, span.attributes["bugsnag.system.memory.spaces.device.size"] as Long)
        assertEquals(300L, span.attributes["bugsnag.system.memory.spaces.device.min"] as Long)
        assertEquals(400L, span.attributes["bugsnag.system.memory.spaces.device.max"] as Long)
        assertEquals(350L, span.attributes["bugsnag.system.memory.spaces.device.mean"] as Long)
        assertEquals(100L, span.attributes["bugsnag.system.memory.spaces.art.min"] as Long)
        assertEquals(200L, span.attributes["bugsnag.system.memory.spaces.art.max"] as Long)
        assertEquals(150L, span.attributes["bugsnag.system.memory.spaces.art.mean"] as Long)
        assertArrayEquals(longArrayOf(100L, 200L), span.attributes["bugsnag.system.memory.spaces.art.used"] as LongArray)
        assertArrayEquals(longArrayOf(300L, 400L), span.attributes["bugsnag.system.memory.spaces.device.used"] as LongArray)
        assertArrayEquals(longArrayOf(1L, 2L), span.attributes["bugsnag.system.memory.timestamps"] as LongArray)

        assertNull(span.attributes["bugsnag.session.memory.runtime.min"])
        assertNull(span.attributes["bugsnag.session.memory.device.min"])
    }

    @Test
    fun testCustomSessionNameFormat() {
        val config = AppSessionConfig(autoStartSession = false)
        val controller = AppSessionSpanController(context, spanFactory, sessionConfig = config)

        ForegroundState.isInForeground = true
        controller.startAppSessionSpan("user checkout-flow")
        controller.endAppSessionSpan()

        val spans = spanProcessor.toList()
        assertEquals(1, spans.size)
        assertEquals("[AppSession/user checkout-flow]", spans[0].name)
        assertEquals("user checkout-flow", spans[0].attributes["bugsnag.app_session.name"])
    }

    @Test
    fun testAppSessionCallbacksObserveLifecycleTransitions() {
        val callback = RecordingSessionCallback()
        ForegroundState.isInForeground = true
        val config = AppSessionConfig(autoStartSession = true, sessionCallbacks = listOf(callback))

        val controller = AppSessionSpanController(context, spanFactory, sessionConfig = config)

        ForegroundState.isInForeground = false
        ForegroundState.isInForeground = true
        controller.endAppSessionSpan()

        val eventTypes = callback.events.map { it.type }
        assertEquals(listOf("started", "backgrounded", "foregrounded", "ended"), eventTypes)

        val sessionIds = callback.events.map { it.session.sessionId }.distinct()
        assertEquals(1, sessionIds.size)
        assertEquals(true, callback.events[0].session.isInForeground)
        assertEquals(false, callback.events[1].session.isInForeground)
        assertEquals(true, callback.events[2].session.isInForeground)
        assertEquals(true, callback.events[3].session.isInForeground)
        assertEquals(CloseReason.CLIENT_END, callback.events[3].session.closeReason)
    }

    @Test
    fun testEndingAStartedSessionResetsIdentityForTheNextSession() {
        val callback = RecordingSessionCallback()
        val config = AppSessionConfig(autoStartSession = false, sessionCallbacks = listOf(callback))
        val controller = AppSessionSpanController(context, spanFactory, sessionConfig = config)

        ForegroundState.isInForeground = true
        controller.startAppSessionSpan()
        controller.endAppSessionSpan()

        val firstSessionId = callback.events.first().session.sessionId

        controller.startAppSessionSpan()
        controller.endAppSessionSpan()

        val secondSessionId = callback.events.last().session.sessionId

        assertTrue(firstSessionId != secondSessionId)
    }

    @Test
    fun testStopRemovesForegroundCallback() {
        ForegroundState.isInForeground = true
        val config = AppSessionConfig(autoStartSession = true)
        val controller = AppSessionSpanController(context, spanFactory, sessionConfig = config)

        controller.stop()

        ForegroundState.isInForeground = false
        ForegroundState.isInForeground = true

        assertEquals(1, spanProcessor.toList().size)
    }
}
