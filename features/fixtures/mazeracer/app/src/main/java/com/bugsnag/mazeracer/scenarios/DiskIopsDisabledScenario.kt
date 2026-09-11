package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.Scenario

/**
 * ROAD 2233 Scenario 10: spans with disk metrics disabled omit iops_* keys.
 */
class DiskIopsDisabledScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 1
        InternalDebug.attachDiskIoSnapshots = true
        config.appSessionConfig.autoStartSession = false
        config.enabledMetrics.disk = false
    }

    override fun startScenario() {
        DiskIopsSupport.ensureProcIoReadable(context)
        BugsnagPerformance.start(config)
        forceConfigureMetrics(config.enabledMetrics)

        runAndFlush {
            val span =
                BugsnagPerformance.startSpan(
                    "DiskIopsDisabled",
                    DiskIopsSupport.diskSpanOptions(disk = true),
                )
            DiskIopsSupport.generateDiskActivity(context, "disabled")
            Thread.sleep(DiskIopsSupport.SPAN_SLEEP_MS)
            span.end()
        }
    }
}
