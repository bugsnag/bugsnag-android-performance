package com.bugsnag.android.performance.internal.appsession

import android.content.Context
import com.bugsnag.android.performance.AppSessionConfig
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.instrumentation.ForegroundState
import com.bugsnag.android.performance.test.CollectingSpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppSessionSpanControllerTest {

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

        val spans = spanProcessor.toList()
        // One span should be started (foreground)
        // Wait, the span is only ended when it's closed.
        // We can't easily see active spans in CollectingSpanProcessor unless they are ended.
        
        controller.endAppSessionSpan()
        val endedSpans = spanProcessor.toList()
        assertEquals(1, endedSpans.size)
        assertEquals("app_session.foreground", endedSpans[0].name)
        assertTrue(endedSpans[0].getAttribute("bugsnag.app.in_foreground") as Boolean)
    }

    @Test
    fun testAutomaticSessionStartInBackground() {
        ForegroundState.isInForeground = false
        val config = AppSessionConfig(autoStartSession = true)
        val controller = AppSessionSpanController(context, spanFactory, sessionConfig = config)

        controller.endAppSessionSpan()
        val endedSpans = spanProcessor.toList()
        assertEquals(1, endedSpans.size)
        assertEquals("app_session.background", endedSpans[0].name)
        assertFalse(endedSpans[0].getAttribute("bugsnag.app.in_foreground") as Boolean)
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
        assertEquals("app_session.foreground", spansAfterBackground[0].name)
        assertEquals("segment_switched", spansAfterBackground[0].getAttribute("bugsnag.session.close_reason"))

        // Transition back to foreground
        ForegroundState.isInForeground = true
        val spansAfterForeground = spanProcessor.toList()
        assertEquals(2, spansAfterForeground.size)
        assertEquals("app_session.background", spansAfterForeground[1].name)
        assertEquals("segment_switched", spansAfterForeground[1].getAttribute("bugsnag.session.close_reason"))
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
        assertEquals("app_session.foreground", spans[0].name)

        ForegroundState.isInForeground = false
        controller.startAppSessionSpan()
        controller.endAppSessionSpan()
        
        val spans2 = spanProcessor.toList()
        assertEquals(2, spans2.size)
        assertEquals("app_session.background", spans2[1].name)
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
        assertEquals(com.bugsnag.android.performance.internal.SpanCategory.APP_SESSION, endedSpans[0].category)
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
            closeReason = "test-reason"
        )

        val json = data.toJson()
        assertEquals("test-session", json.getString("sessionId"))
        assertEquals(1, json.getInt("index"))
        assertEquals("test-name", json.getString("appSessionName"))

        val fromJson = AppSessionData.fromJson(json)
        assertEquals("test-session", fromJson.sessionId)
        assertEquals(1, fromJson.index)
        assertEquals("test-name", fromJson.appSessionName)
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
        assertEquals("user checkout-flow", spans[0].getAttribute("bugsnag.app_session.name"))
    }
}
