package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanMetrics
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.internal.framerate.FramerateMetricsSnapshot
import com.bugsnag.android.performance.internal.framerate.FramerateMetricsSource
import com.bugsnag.android.performance.internal.framerate.TimestampPairBuffer
import com.bugsnag.android.performance.internal.metrics.MetricSource
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestMetricsContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class SpanFactoryTest {
    /**
     * Avoids us needing to mock SystemClock for this test
     */
    private val baseOptions = SpanOptions.startTime(1L)

    private lateinit var frameMetrics: MetricSource<FramerateMetricsSnapshot>

    private lateinit var spanFactory: SpanFactory

    private val spanMetricsWithRendering =
        SpanMetrics(rendering = true, cpu = false, memory = false)
    private val spanMetricsWithoutRendering =
        SpanMetrics(rendering = false, cpu = false, memory = false)

    @Before
    fun setup() {
        frameMetrics =
            object : MetricSource<FramerateMetricsSnapshot> {
                private val timestampPairBuffer = TimestampPairBuffer()
                var counter = 0L

                override fun createStartMetrics(): FramerateMetricsSnapshot {
                    counter++
                    return FramerateMetricsSnapshot(counter, counter, counter, timestampPairBuffer, 0)
                }

                override fun endMetrics(
                    startMetrics: FramerateMetricsSnapshot,
                    span: Span,
                ) = Unit
            }

        spanFactory =
            SpanFactory(
                NoopSpanProcessor,
                spanAttributeSource = {},
                metricsContainer =
                    TestMetricsContainer(
                        frames = FramerateMetricsSource(),
                    ),
            )
        spanFactory.attach(mock())
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
                    .withMetrics(spanMetricsWithRendering),
            ).metrics,
        )

        assertNotNull(
            "setFirstClass(true).withRenderingMetrics(true) should have rendering metrics",
            spanFactory.createCustomSpan(
                "Scrolling",
                baseOptions
                    .setFirstClass(true)
                    .withMetrics(spanMetricsWithRendering),
            ).metrics,
        )

        assertNotNull(
            "setFirstClass(true).withRenderingMetrics(true) should have rendering metrics",
            spanFactory.createCustomSpan(
                "Scrolling",
                baseOptions
                    .setFirstClass(true)
                    .withMetrics(null),
            ).metrics,
        )

        val metrics =
            spanFactory.createCustomSpan(
                "Scrolling",
                baseOptions.setFirstClass(false),
            ).metrics
        assertNull(
            "SpanOptions.setFirstClass(false) should not have rendering metrics",
            metrics,
        )

        @Suppress("DEPRECATION")
        assertNull(
            "SpanOptions.setFirstClass(true).withRenderingMetrics(false) should not have rendering metrics",
            spanFactory.createCustomSpan(
                "Scrolling",
                baseOptions
                    .setFirstClass(true)
                    .withRenderingMetrics(false),
            ).metrics,
        )

        assertNull(
            "SpanOptions.setFirstClass(true).withRenderingMetrics(false) should not have rendering metrics",
            spanFactory.createCustomSpan(
                "Scrolling",
                baseOptions
                    .setFirstClass(true)
                    .withMetrics(spanMetricsWithoutRendering),
            ).metrics,
        )
    }

    @Test
    fun testSamplingProbability() {
        spanFactory.sampler = ProbabilitySampler(0.5)

        val span = spanFactory.createCustomSpan("Test", baseOptions)
        assertEquals(0.5, span.samplingProbability, 0.01)
    }
}
