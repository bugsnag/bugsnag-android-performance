package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanMetrics
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.internal.framerate.FramerateMetricsSnapshot
import com.bugsnag.android.performance.internal.framerate.TimestampPairBuffer
import com.bugsnag.android.performance.internal.metrics.CpuMetricsSnapshot
import com.bugsnag.android.performance.internal.metrics.MemoryMetricsSnapshot
import com.bugsnag.android.performance.internal.metrics.MetricSource
import com.bugsnag.android.performance.internal.metrics.SampledMetricSource
import com.bugsnag.android.performance.test.TestMetricsContainer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PayloadDumpTest {
    @Test
    fun dumpTracePayload() {
        // Create simple metric sources that attach attributes when ended
        val cpuMetrics =
            object : SampledMetricSource<CpuMetricsSnapshot> {
                override fun run() = Unit

                override fun createStartMetrics(): CpuMetricsSnapshot = CpuMetricsSnapshot(0)

                override fun endMetrics(
                    startMetrics: CpuMetricsSnapshot,
                    span: Span,
                ) {
                    span.setAttribute("sampled.cpu.attached", true)
                }
            }

        val memoryMetrics =
            object : SampledMetricSource<MemoryMetricsSnapshot> {
                override fun run() = Unit

                override fun createStartMetrics(): MemoryMetricsSnapshot = MemoryMetricsSnapshot(0)

                override fun endMetrics(
                    startMetrics: MemoryMetricsSnapshot,
                    span: Span,
                ) {
                    span.setAttribute("sampled.memory.attached", true)
                }
            }

        val frameMetrics =
            object : MetricSource<FramerateMetricsSnapshot> {
                private val buffer = TimestampPairBuffer()

                override fun createStartMetrics(): FramerateMetricsSnapshot = FramerateMetricsSnapshot(1, 1, 1, buffer, 0)

                override fun endMetrics(
                    startMetrics: FramerateMetricsSnapshot,
                    span: Span,
                ) {
                    span.setAttribute("sampled.rendering.attached", true)
                }
            }

        val metricsContainer = TestMetricsContainer(cpu = cpuMetrics, memory = memoryMetrics, frames = frameMetrics)

        // Simple span processor that does nothing
        val noopProcessor =
            object : SpanProcessor {
                override fun onEnd(span: Span) {}
            }

        val spanFactory = SpanFactory(noopProcessor, spanAttributeSource = {}, metricsContainer = metricsContainer)
        spanFactory.attach(org.mockito.kotlin.mock())

        // Create different span types
        val custom =
            spanFactory.createCustomSpan(
                "custom",
                SpanOptions.startTime(1L).setFirstClass(false).withMetrics(SpanMetrics(rendering = true, cpu = true, memory = true)),
            )
        val network =
            spanFactory.createNetworkSpan(
                "https://example.com",
                "GET",
                SpanOptions.startTime(2L).setFirstClass(false).withMetrics(SpanMetrics(rendering = false, cpu = true, memory = true)),
            )
        val view =
            spanFactory.createViewLoadSpan(
                com.bugsnag.android.performance.ViewType.ACTIVITY,
                "MainActivity",
                SpanOptions.startTime(3L).setFirstClass(true),
            )
        val appStart = spanFactory.createAppStartSpan("Cold")

        // End spans to trigger metric collection
        custom.end()
        network?.end()
        view.end()
        appStart.end()

        val spans =
            listOfNotNull(
                custom,
                network,
                view,
                appStart,
            )

        val payload = TracePayload.createTracePayload("TEST_API_KEY", spans, Attributes(), false, null)

        // Write headers and body to a temp file for inspection
        val outFile = java.io.File("build/trace_payload.json")
        outFile.parentFile?.mkdirs()
        outFile.printWriter().use { pw ->
            pw.println("--- TracePayload headers ---")
            payload.headers.forEach { (k, v) -> pw.println("$k: $v") }
            pw.println("--- TracePayload body (as UTF-8) ---")
            // attempt to decode if gzipped
            val bodyStr =
                try {
                    if (payload.headers["Content-Encoding"] == "gzip") {
                        java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(payload.body)).bufferedReader().use { it.readText() }
                    } else {
                        String(payload.body)
                    }
                } catch (_: Exception) {
                    String(payload.body)
                }
            pw.println(bodyStr)
        }
        println("Wrote payload to: ${outFile.absolutePath}")
    }
}
