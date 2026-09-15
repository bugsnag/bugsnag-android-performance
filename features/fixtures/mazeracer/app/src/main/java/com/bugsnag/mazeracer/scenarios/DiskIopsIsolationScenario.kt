package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.Scenario

/**
 * ROAD 2233 Scenario 13: CPU and memory metrics remain present whether disk IOPS is on or off.
 * Frozen-frame attrs need view frames and stay covered by unit tests / FrameMetricsScenario.
 */
class DiskIopsIsolationScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 1
        InternalDebug.attachDiskIoSnapshots = true
        config.appSessionConfig.autoStartSession = false
        config.enabledMetrics.cpu = true
        config.enabledMetrics.memory = true
        config.enabledMetrics.disk = scenarioMetadata != "disabled"
    }

    override fun startScenario() {
        DiskIopsSupport.ensureProcIoReadable(context)
        BugsnagPerformance.start(config)
        forceConfigureMetrics(config.enabledMetrics)

        runAndFlush {
            val span =
                BugsnagPerformance.startSpan(
                    "DiskIopsIsolation",
                    DiskIopsSupport.diskSpanOptions(
                        disk = config.enabledMetrics.disk,
                        cpu = true,
                        memory = true,
                    ),
                )
            DiskIopsSupport.generateDiskActivity(context, "isolation")
            Thread.sleep(DiskIopsSupport.SAMPLER_TIMEOUT_MS)
            span.end()
        }
    }
}
