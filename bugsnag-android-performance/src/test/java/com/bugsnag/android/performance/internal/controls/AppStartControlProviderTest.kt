package com.bugsnag.android.performance.internal.controls

import com.bugsnag.android.performance.controls.AppStartControlProvider
import com.bugsnag.android.performance.controls.SpanType
import com.bugsnag.android.performance.internal.AppStartTracker.Companion.appStartToken
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanTracker
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AppStartControlProviderTest {

    private lateinit var spanTracker: SpanTracker
    private lateinit var provider: AppStartControlProvider
    private lateinit var testSpan: SpanImpl
    private val spanFactory = TestSpanFactory()

    @Before
    fun setup() {
        testSpan = spanFactory.newSpan(
            name = "[AppStart/AndroidCold]",
            endTime = null,
            processor = NoopSpanProcessor,
        )
        spanTracker = SpanTracker()
        provider = AppStartControlProvider(spanTracker)
    }

    @Test
    fun `no app start span exists`() {
        val control = provider[SpanType.AppStart]
        assertNull(control)
    }

    @Test
    fun `an app start span exists`() {
        spanTracker.associate(appStartToken) { testSpan }
        val control = provider[SpanType.AppStart]
        assertNotNull(control)
    }

    @Test
    fun `setType updates span name attribute`() {
        spanTracker.associate(appStartToken) { testSpan }
        val control = provider[SpanType.AppStart]

        control?.setType("FirstStart")
        val attributeName = testSpan.attributes["bugsnag.app_start.name"]
        assertEquals("[AppStart/AndroidCold]FirstStart", testSpan.name)
        assertEquals("FirstStart", attributeName)
    }

    @Test
    fun `clearType removes type attribute`() {
        spanTracker.associate(appStartToken) { testSpan }
        val control = provider[SpanType.AppStart]

        control?.setType("test")
        control?.clearType()
        val attributeName = testSpan.attributes["bugsnag.app_start.name"]
        assertEquals("[AppStart/AndroidCold]", testSpan.name)
        assertEquals(null, attributeName)
    }
}
