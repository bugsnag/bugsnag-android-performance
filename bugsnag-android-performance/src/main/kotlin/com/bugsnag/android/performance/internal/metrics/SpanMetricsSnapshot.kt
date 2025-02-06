package com.bugsnag.android.performance.internal.metrics

import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.framerate.FramerateMetricsSnapshot

/**
 * Container for all of the possible metrics types that can attached to a [Span].
 */
internal class SpanMetricsSnapshot(
    private val renderingMetricsSource: MetricSource<FramerateMetricsSnapshot>?,
    private val cpuMetricsSource: MetricSource<CpuMetricsSnapshot>?,
) {
    private val renderingMetricsSnapshot: FramerateMetricsSnapshot? =
        renderingMetricsSource?.createStartMetrics()

    private val cpuMetricsCpuMetricsSnapshot: CpuMetricsSnapshot? =
        cpuMetricsSource?.createStartMetrics()

    fun finish(spanImpl: SpanImpl) {
        renderingMetricsSnapshot?.let { renderingMetricsSource?.endMetrics(it, spanImpl) }
        cpuMetricsCpuMetricsSnapshot?.let { cpuMetricsSource?.endMetrics(it, spanImpl) }
    }
}
