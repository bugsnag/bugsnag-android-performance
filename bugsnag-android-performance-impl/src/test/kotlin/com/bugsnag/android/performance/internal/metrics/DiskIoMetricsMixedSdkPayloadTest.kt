package com.bugsnag.android.performance.internal.metrics

import com.bugsnag.android.performance.internal.Attributes
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.TracePayload
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.OtelValidator.assertTraceDataValid
import com.bugsnag.android.performance.test.TestSpanFactory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ROAD 2233 – Scenario 14 (Android SDK scope):
 * Mixed old/new SDK spans in one OTLP batch: only new-SDK spans include disk IOPS keys.
 *
 * Backend/API aggregation (disk.iops_total.span_count = 980, percentiles on reporting spans
 * only, CPU/memory over all 1240) is outside this repository. This test uses a 98:26 scale
 * model of the ROAD 980:260 example and verifies the delivered payload shape the API relies on.
 */
@RunWith(RobolectricTestRunner::class)
internal class DiskIoMetricsMixedSdkPayloadTest {
    @Test
    fun mixedSdkBatchIncludesDiskIopsOnlyOnNewSdkSpans() {
        val spans = buildMixedSdkSpanBatch()
        assertEquals(TOTAL_SPAN_COUNT, spans.size)

        val payload =
            TracePayload.encodeSpanPayload(
                spans,
                Attributes().also { attrs ->
                    attrs["service.name"] = "Test app"
                    attrs["telemetry.sdk.name"] = "bugsnag.performance.android"
                    attrs["telemetry.sdk.version"] = "0.0.0"
                },
                null,
            )

        assertTraceDataValid(payload)

        val spansInPayload = spansFromPayload(JSONObject(payload.toString(Charsets.UTF_8)))
        assertEquals(TOTAL_SPAN_COUNT, spansInPayload.size)

        val diskReportingCount = spansInPayload.count { it.reportsDiskIopsTotal() }
        val cpuReportingCount = spansInPayload.count { it.reportsCpuMean() }

        assertEquals(NEW_SDK_SPAN_COUNT, diskReportingCount)
        assertEquals(TOTAL_SPAN_COUNT, cpuReportingCount)

        spansInPayload.forEach { spanAttributes ->
            if (spanAttributes.reportsDiskIopsTotal()) {
                assertTrue(spanAttributes.hasDiskIopsRead())
                assertTrue(spanAttributes.hasDiskIopsWrite())
            } else {
                assertFalse(spanAttributes.hasDiskIopsRead())
                assertFalse(spanAttributes.hasDiskIopsWrite())
                assertFalse(spanAttributes.hasDiskIopsTotal())
            }
        }
    }

    private fun buildMixedSdkSpanBatch(): List<SpanImpl> {
        val spans = ArrayList<SpanImpl>(TOTAL_SPAN_COUNT)
        var endTime = 1_000_000_000L

        repeat(OLD_SDK_SPAN_COUNT) { index ->
            spans.add(createOldSdkSpan(endTime = endTime + index))
        }

        repeat(NEW_SDK_SPAN_COUNT) { index ->
            spans.add(
                createNewSdkSpan(
                    endTime = endTime + OLD_SDK_SPAN_COUNT + index,
                    iopsTotal = 10.0 + index,
                ),
            )
        }

        return spans
    }

    private fun createOldSdkSpan(endTime: Long): SpanImpl {
        val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
        span.setAttribute(ATTR_CPU_MEAN, EXPECTED_CPU_MEAN)
        span.end(endTime)
        return span
    }

    private fun createNewSdkSpan(
        endTime: Long,
        iopsTotal: Double,
    ): SpanImpl {
        val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
        span.setAttribute(ATTR_CPU_MEAN, EXPECTED_CPU_MEAN)
        span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ] = iopsTotal * 0.6
        span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE] = iopsTotal * 0.4
        span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL] = iopsTotal
        span.end(endTime)
        return span
    }

    private fun spansFromPayload(payload: JSONObject): List<SpanAttributeView> {
        val attributesArray =
            payload
                .getJSONArray("resourceSpans")
                .getJSONObject(0)
                .getJSONArray("scopeSpans")
                .getJSONObject(0)
                .getJSONArray("spans")

        return List(attributesArray.length()) { index ->
            SpanAttributeView(attributesArray.getJSONObject(index).getJSONArray("attributes"))
        }
    }

    private class SpanAttributeView(
        private val attributes: JSONArray,
    ) {
        fun reportsDiskIopsTotal(): Boolean = hasKey(DiskIoMetricsSource.ATTR_IOPS_TOTAL)

        fun hasDiskIopsRead(): Boolean = hasKey(DiskIoMetricsSource.ATTR_IOPS_READ)

        fun hasDiskIopsWrite(): Boolean = hasKey(DiskIoMetricsSource.ATTR_IOPS_WRITE)

        fun hasDiskIopsTotal(): Boolean = hasKey(DiskIoMetricsSource.ATTR_IOPS_TOTAL)

        fun reportsCpuMean(): Boolean = hasKey(ATTR_CPU_MEAN)

        private fun hasKey(key: String): Boolean {
            for (index in 0 until attributes.length()) {
                if (attributes.getJSONObject(index).getString("key") == key) {
                    return true
                }
            }
            return false
        }
    }

    private companion object {
        // Scaled 1:10 from ROAD example (980 new + 260 old = 1240 total).
        private const val NEW_SDK_SPAN_COUNT = 98
        private const val OLD_SDK_SPAN_COUNT = 26
        private const val TOTAL_SPAN_COUNT = NEW_SDK_SPAN_COUNT + OLD_SDK_SPAN_COUNT

        private const val ATTR_CPU_MEAN = "bugsnag.system.cpu_mean_total"
        private const val EXPECTED_CPU_MEAN = 10.0
    }
}
