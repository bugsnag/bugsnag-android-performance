package com.bugsnag.android.performance.internal.metrics

import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.processing.JsonTraceWriter
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.StringWriter

/**
 * ROAD 2233 – Scenario 10 (Android SDK scope):
 * Spans without disk IOPS attributes are accepted and delivered without disk keys.
 *
 * Pipeline storage (-1 default) and API null responses are backend concerns outside this
 * repository. Here we verify the SDK emits spans with no bugsnag.device.disk.iops_* keys
 * when disk metrics are absent (as with an older SDK), while other span attributes remain.
 */
@RunWith(RobolectricTestRunner::class)
internal class DiskIoMetricsLegacySdkTest {
    @Test
    fun spanWithoutDiskMetricsOmitsDiskAttrsFromPayload() {
        val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
        span.setAttribute("fps.average", 60.0)
        span.end(2_000_000_000L)

        assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
        assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
        assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])
        assertEquals(60.0, span.attributes["fps.average"])

        val json =
            StringWriter().apply {
                JsonTraceWriter(this).use { writer -> span.toJson(writer) }
            }.toString()

        assertFalse(json.contains(DiskIoMetricsSource.ATTR_IOPS_READ))
        assertFalse(json.contains(DiskIoMetricsSource.ATTR_IOPS_WRITE))
        assertFalse(json.contains(DiskIoMetricsSource.ATTR_IOPS_TOTAL))
        assertFalse(json.contains("\"doubleValue\":null"))
    }

    @Test
    fun otherMetricsUnaffectedWhenDiskSourceAbsent() {
        val otherMetricsSource =
            object : MetricSource<CpuMetricsSnapshot> {
                override fun createStartMetrics(): CpuMetricsSnapshot = CpuMetricsSnapshot(0)

                override fun endMetrics(
                    startMetrics: CpuMetricsSnapshot,
                    span: Span,
                ) {
                    (span as SpanImpl).attributes["bugsnag.device.cpu.usage"] = 25.0
                }
            }

        val snapshot =
            SpanMetricsSnapshot.createIfRequired(
                renderingMetricsSource = null,
                cpuMetricsSource = otherMetricsSource,
                memoryMetricsSource = null,
                diskIoMetricsSource = null,
            )
        assertNotNull(snapshot)

        val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
        snapshot!!.finish(span)
        span.end(2_000_000_000L)

        assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
        assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
        assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])
        assertEquals(25.0, span.attributes["bugsnag.device.cpu.usage"])
    }
}
