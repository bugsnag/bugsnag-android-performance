package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.internal.framerate.RenderMetricsSnapshot
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

    private lateinit var frameMetrics: MetricSource<RenderMetricsSnapshot>

    private lateinit var spanFactory: SpanFactory

    @Before
    fun setup() {
        frameMetrics = object : MetricSource<RenderMetricsSnapshot> {
            var counter = 0L

            override fun createStartMetrics(): RenderMetricsSnapshot {
                counter++
                return RenderMetricsSnapshot(counter, counter, counter, null)
            }

            override fun endMetrics(startMetrics: RenderMetricsSnapshot, span: Span) = Unit
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
            ).startFrameMetrics,
        )

        assertNotNull(
            "withRenderingMetrics(true) should have rendering metrics",
            spanFactory.createCustomSpan(
                "Scrolling",
                baseOptions
                    .setFirstClass(false)
                    .withRenderingMetrics(true),
            ).startFrameMetrics,
        )

        assertNotNull(
            "setFirstClass(true).withRenderingMetrics(true) should have rendering metrics",
            spanFactory.createCustomSpan(
                "Scrolling",
                baseOptions
                    .setFirstClass(true)
                    .withRenderingMetrics(true),
            ).startFrameMetrics,
        )

        assertNull(
            "SpanOptions.setFirstClass(false) should not have rendering metrics",
            spanFactory.createCustomSpan(
                "Scrolling",
                baseOptions.setFirstClass(false),
            ).startFrameMetrics,
        )

        assertNull(
            "SpanOptions.setFirstClass(true).withRenderingMetrics(false) should not have rendering metrics",
            spanFactory.createCustomSpan(
                "Scrolling",
                baseOptions
                    .setFirstClass(true)
                    .withRenderingMetrics(false),
            ).startFrameMetrics,
        )
    }
}
