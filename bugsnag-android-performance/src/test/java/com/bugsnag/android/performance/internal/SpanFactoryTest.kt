package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.internal.framerate.FramerateMetricsSnapshot
import com.bugsnag.android.performance.internal.framerate.TimestampPairBuffer
import com.bugsnag.android.performance.internal.metrics.MetricSource
import com.bugsnag.android.performance.test.NoopSpanProcessor
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SpanFactoryTest {
    /**
     * Avoids us needing to mock SystemClock for this test
     */
    private val baseOptions = SpanOptions.startTime(1L)

    private lateinit var frameMetrics: MetricSource<FramerateMetricsSnapshot>

    private lateinit var spanFactory: SpanFactory

    @Before
    fun setup() {
        frameMetrics = object : MetricSource<FramerateMetricsSnapshot> {
            private val timestampPairBuffer = TimestampPairBuffer()
            var counter = 0L

            override fun createStartMetrics(): FramerateMetricsSnapshot {
                counter++
                return FramerateMetricsSnapshot(counter, counter, counter, timestampPairBuffer, 0)
            }

            override fun endMetrics(startMetrics: FramerateMetricsSnapshot, span: Span) = Unit
        }

        spanFactory = SpanFactory(NoopSpanProcessor)
        spanFactory.framerateMetricsSource = frameMetrics
    }

    @Test
    fun testRenderMetricsOptions() {
        assertNotNull(
            "firstClass(true) should have rendering metrics",
            spanFactory.createCustomSpan(
                "First Class",
                baseOptions.setFirstClass(true),
            ).metrics,
        )

        assertNotNull(
            "withRenderingMetrics(true) should have rendering metrics",
            spanFactory.createCustomSpan(
                "Scrolling",
                baseOptions
                    .setFirstClass(false)
                    .withRenderingMetrics(true),
            ).metrics,
        )

        assertNotNull(
            "setFirstClass(true).withRenderingMetrics(true) should have rendering metrics",
            spanFactory.createCustomSpan(
                "Scrolling",
                baseOptions
                    .setFirstClass(true)
                    .withRenderingMetrics(true),
            ).metrics,
        )

        assertNull(
            "SpanOptions.setFirstClass(false) should not have rendering metrics",
            spanFactory.createCustomSpan(
                "Scrolling",
                baseOptions.setFirstClass(false),
            ).metrics,
        )

        assertNull(
            "SpanOptions.setFirstClass(true).withRenderingMetrics(false) should not have rendering metrics",
            spanFactory.createCustomSpan(
                "Scrolling",
                baseOptions
                    .setFirstClass(true)
                    .withRenderingMetrics(false),
            ).metrics,
        )
    }
}
