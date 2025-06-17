package com.bugsnag.android.performance.internal.metrics

import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Build
import com.bugsnag.android.performance.EnabledMetrics
import com.bugsnag.android.performance.SpanMetrics
import com.bugsnag.android.performance.internal.framerate.FramerateMetricsSnapshot
import com.bugsnag.android.performance.internal.framerate.FramerateMetricsSource
import com.bugsnag.android.performance.internal.processing.SamplerExecutor

/**
 * Encapsulates the various metrics that can be associated with spans, measuring various resource
 * use (cpu, memory, rendering times) for a span.
 */
internal open class MetricsContainer(private val samplerExecutor: SamplerExecutor) {
    private var application: Application? = null

    var memoryMetricSource: SampledMetricSource<MemoryMetricsSnapshot>? = null
        private set

    var renderingMetricsSource: MetricSource<FramerateMetricsSnapshot>? = null
        private set

    var cpuMetricSource: SampledMetricSource<CpuMetricsSnapshot>? = null
        private set

    /**
     * Called before we are fully configured, typically from `InstrumentedAppState.attach`. This
     * installs all of the metrics instrumentation - which can then be uninstalled when
     * `BugsnagPerformance.start` is called.
     */
    fun attach(application: Application) {
        this.application = application

        memoryMetricSource = startSampling(createMemoryMetricSource(application))
        cpuMetricSource = startSampling(createCpuMetricSource(application))
        renderingMetricsSource = createFrameMetricSource(application)
    }

    /**
     * Apply the specific `EnabledMetrics` - installing & uninstalling existing metric sources
     * as required to match the given configuration.
     */
    fun configure(enabledMetrics: EnabledMetrics) {
        if (!enabledMetrics.cpu) {
            stopSampling(cpuMetricSource)
            cpuMetricSource = null
        }

        if (!enabledMetrics.memory) {
            stopSampling(memoryMetricSource)
            memoryMetricSource = null
        }

        if (!enabledMetrics.rendering) {
            (renderingMetricsSource as? ActivityLifecycleCallbacks)?.let {
                application?.unregisterActivityLifecycleCallbacks(it)
            }

            renderingMetricsSource = null
        }
    }

    private fun isEnabled(): Boolean {
        return memoryMetricSource != null || renderingMetricsSource != null || cpuMetricSource != null
    }

    fun createSpanMetricsSnapshot(
        defaultEnabled: Boolean,
        spanMetrics: SpanMetrics?,
    ): SpanMetricsSnapshot? {
        if (!isEnabled()) {
            return null
        }

        return SpanMetricsSnapshot.createIfRequired(
            renderingMetricsSource?.takeIf { spanMetrics?.rendering ?: defaultEnabled },
            cpuMetricSource?.takeIf { spanMetrics?.cpu ?: defaultEnabled },
            memoryMetricSource?.takeIf { spanMetrics?.memory ?: defaultEnabled },
        )
    }

    protected open fun createCpuMetricSource(application: Application): SampledMetricSource<CpuMetricsSnapshot>? {
        return CpuMetricsSource(SAMPLING_DELAY_MS)
    }

    protected open fun createMemoryMetricSource(application: Application): SampledMetricSource<MemoryMetricsSnapshot>? {
        return MemoryMetricsSource(application, SAMPLING_DELAY_MS)
    }

    protected open fun createFrameMetricSource(application: Application): MetricSource<FramerateMetricsSnapshot>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return FramerateMetricsSource()
                .also { application.registerActivityLifecycleCallbacks(it) }
        }

        return null
    }

    private fun <T : Runnable?> startSampling(sampler: T): T {
        sampler?.let { samplerExecutor.addSampler(it, SAMPLING_DELAY_MS) }
        return sampler
    }

    private fun stopSampling(sampler: Runnable?) {
        if (sampler != null) {
            samplerExecutor.removeSampler(sampler)
        }
    }

    private companion object {
        const val SAMPLING_DELAY_MS = 1000L
    }
}
