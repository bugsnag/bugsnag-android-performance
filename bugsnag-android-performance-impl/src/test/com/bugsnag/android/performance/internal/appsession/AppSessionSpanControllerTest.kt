package com.bugsnag.android.performance.internal.appsession

import android.content.Context
import com.bugsnag.android.performance.AppSessionConfig
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanProcessor
import com.bugsnag.android.performance.internal.instrumentation.ForegroundState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
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

    private lateinit var spanFactory: SpanFactory
    private lateinit var spanProcessor: CollectingSpanProcessor
    private lateinit var context: Context

    @Before
    fun setup() {
        spanProcessor = CollectingSpanProcessor()
        spanFactory = SpanFactory(spanProcessor)
        context = mock()
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
        val controller = AppSessionSpanController(context, spanFactory, sessionConfig = config)

        controller.endAppSessionSpan()
        val endedSpans = spanProcessor.toList()
        assertEquals(1, endedSpans.size)
        assertEquals("[AppSession/background]", endedSpans[0].name)
        assertFalse(endedSpans[0].attributes["bugsnag.app.in_foreground"] as Boolean)
    }

    @Test
    fun testAutomaticTransition() {
        ForegroundState.isInForeground = true
        val config = AppSessionConfig(autoStartSession = true)
        val controller = AppSessionSpanController(context, spanFactory, sessionConfig = config)

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
    fun testAutomaticTransitionUsesCustomDefaultName() {
        ForegroundState.isInForeground = true
        val config = AppSessionConfig(autoStartSession = true, manualSessionDefaultName = "customname")
        val controller = AppSessionSpanController(context, spanFactory, sessionConfig = config)

        val initialSpans = spanProcessor.toList()
        assertEquals(1, initialSpans.size)
        assertEquals("[AppSession/customname]", initialSpans[0].name)
        assertEquals("customname", initialSpans[0].attributes["bugsnag.app_session.name"])

        ForegroundState.isInForeground = false
        val spansAfterBackground = spanProcessor.toList()
        assertEquals(2, spansAfterBackground.size)
        assertEquals("[AppSession/customname]", spansAfterBackground[1].name)
        assertEquals("customname", spansAfterBackground[1].attributes["bugsnag.app_session.name"])
        assertEquals("segment_switched", spansAfterBackground[1].attributes["bugsnag.session.close_reason"])

        ForegroundState.isInForeground = true
        val spansAfterForeground = spanProcessor.toList()
        assertEquals(3, spansAfterForeground.size)
        assertEquals("[AppSession/customname]", spansAfterForeground[2].name)
        assertEquals("customname", spansAfterForeground[2].attributes["bugsnag.app_session.name"])
        assertEquals("segment_switched", spansAfterForeground[2].attributes["bugsnag.session.close_reason"])
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
    fun testManualStartUsesConfiguredDefaultName() {
        val config = AppSessionConfig(autoStartSession = false, manualSessionDefaultName = "customname")
        val controller = AppSessionSpanController(context, spanFactory, sessionConfig = config)

        ForegroundState.isInForeground = true
        controller.startAppSessionSpan()
        controller.endAppSessionSpan()

        val spans = spanProcessor.toList()
        assertEquals(1, spans.size)
        assertEquals("[AppSession/customname]", spans[0].name)
        assertEquals("customname", spans[0].attributes["bugsnag.app_session.name"])

        ForegroundState.isInForeground = false
        controller.startAppSessionSpan()
        controller.endAppSessionSpan()

        val spans2 = spanProcessor.toList()
        assertEquals(2, spans2.size)
        assertEquals("[AppSession/customname]", spans2[1].name)
        assertEquals("customname", spans2[1].attributes["bugsnag.app_session.name"])
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
        val data = AppSessionData(
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
}
