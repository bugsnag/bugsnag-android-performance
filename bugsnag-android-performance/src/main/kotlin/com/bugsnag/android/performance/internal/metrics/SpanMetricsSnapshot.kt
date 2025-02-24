package com.bugsnag.android.performance.internal.metrics

import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.framerate.FramerateMetricsSnapshot

/**
 * Container for all of the possible metrics types that can attached to a [Span].
 */
internal class SpanMetricsSnapshot(
    private val renderingMetricsSource: MetricSource<FramerateMetricsSnapshot>?,
    private val cpuMetricsSource: MetricSource<CpuMetricsSnapshot>?,
    private val memoryMetricsSource: MetricSource<MemoryMetricsSnapshot>?,
) {
    private val renderingMetricsSnapshot: FramerateMetricsSnapshot? =
        renderingMetricsSource?.createStartMetrics()

    private val cpuMetricsCpuMetricsSnapshot: CpuMetricsSnapshot? =
        cpuMetricsSource?.createStartMetrics()

    private val memoryMetricsMemoryMetricsSnapshot: MemoryMetricsSnapshot? =
        memoryMetricsSource?.createStartMetrics()

    fun finish(spanImpl: SpanImpl) {
        renderingMetricsSnapshot?.let { renderingMetricsSource?.endMetrics(it, spanImpl) }
        cpuMetricsCpuMetricsSnapshot?.let { cpuMetricsSource?.endMetrics(it, spanImpl) }
        memoryMetricsMemoryMetricsSnapshot?.let { memoryMetricsSource?.endMetrics(it, spanImpl) }
    }

    internal companion object {
        /**
         * Create a `SpanMetricsSnapshot` only if one is required (ie: at least one of the
         * `MetricSource`s is non-null). If all of the `MetricSource`s passed here are `null`
         * then this function will return `null`.
         */
        fun createIfRequired(
            renderingMetricsSource: MetricSource<FramerateMetricsSnapshot>?,
            cpuMetricsSource: MetricSource<CpuMetricsSnapshot>?,
            memoryMetricsSource: MetricSource<MemoryMetricsSnapshot>?,
        ): SpanMetricsSnapshot? {
            if (renderingMetricsSource == null &&
                cpuMetricsSource == null &&
                memoryMetricsSource == null
            ) {
                return null
            }

            return SpanMetricsSnapshot(
                renderingMetricsSource,
                cpuMetricsSource,
                memoryMetricsSource,
            )
        }
    }
}
