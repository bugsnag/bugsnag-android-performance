package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.Scenario

/**
 * ROAD 2233 Scenario 14 (SDK scope): one batch with a disk-reporting span and a disk-omitted span.
 * Pipeline span_count / percentiles are backend concerns.
 */
class DiskIopsMixedScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 2
        InternalDebug.attachDiskIoSnapshots = true
        config.appSessionConfig.autoStartSession = false
        config.enabledMetrics.disk = true
        config.enabledMetrics.cpu = true
    }

    override fun startScenario() {
        DiskIopsSupport.ensureProcIoReadable(context)
        BugsnagPerformance.start(config)
        forceConfigureMetrics(config.enabledMetrics)

        runAndFlush {
            val newSdk =
                BugsnagPerformance.startSpan(
                    "DiskIopsNewSdk",
                    DiskIopsSupport.diskSpanOptions(disk = true, cpu = true),
                )
            val oldSdk =
                BugsnagPerformance.startSpan(
                    "DiskIopsOldSdk",
                    DiskIopsSupport.diskSpanOptions(disk = false, cpu = true),
                )
            DiskIopsSupport.generateDiskActivity(context, "mixed")
            Thread.sleep(DiskIopsSupport.SAMPLER_TIMEOUT_MS)
            newSdk.end()
            oldSdk.end()
        }
    }
}
