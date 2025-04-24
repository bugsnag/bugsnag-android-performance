package com.bugsnag.android.performance.test

import android.app.Application
import com.bugsnag.android.performance.internal.framerate.FramerateMetricsSnapshot
import com.bugsnag.android.performance.internal.metrics.CpuMetricsSnapshot
import com.bugsnag.android.performance.internal.metrics.MemoryMetricsSnapshot
import com.bugsnag.android.performance.internal.metrics.MetricSource
import com.bugsnag.android.performance.internal.metrics.MetricsContainer
import com.bugsnag.android.performance.internal.metrics.SampledMetricSource

/**
 * Test-friendly implementation of `MetricsContainer`
 */
internal class TestMetricsContainer(
    private val cpu: SampledMetricSource<CpuMetricsSnapshot>? = null,
    private val memory: SampledMetricSource<MemoryMetricsSnapshot>? = null,
    private val frames: MetricSource<FramerateMetricsSnapshot>? = null,
) : MetricsContainer(TestSamplerExecutor()) {
    override fun createCpuMetricSource(
        application: Application,
    ): SampledMetricSource<CpuMetricsSnapshot>? {
        return cpu
    }

    override fun createMemoryMetricSource(
        application: Application,
    ): SampledMetricSource<MemoryMetricsSnapshot>? {
        return memory
    }

    override fun createFrameMetricSource(
        application: Application,
    ): MetricSource<FramerateMetricsSnapshot>? {
        return frames
    }
}
