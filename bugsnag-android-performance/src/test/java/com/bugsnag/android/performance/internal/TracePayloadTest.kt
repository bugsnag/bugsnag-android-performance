package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class TracePayloadTest {
    private lateinit var spanFactory: TestSpanFactory

    @Before
    fun createSpanFactory() {
        spanFactory = TestSpanFactory()
    }

    @Test
    fun testSamplerHeader() {
        val spans = listOf(
            createSpan(0.3),
            createSpan(0.1),
            createSpan(0.5),
            createSpan(0.3),
            createSpan(0.5),
            createSpan(0.3),
        )

        val tracePayload = TracePayload.createTracePayload("abc123", spans, Attributes())
        assertEquals(
            "0.1:1;0.3:3;0.5:2",
            tracePayload.headers["Bugsnag-Span-Sampling"],
        )
    }

    private fun createSpan(pValue: Double): SpanImpl =
        spanFactory.newSpan(processor = NoopSpanProcessor).apply {
            samplingProbability = pValue
        }
}
