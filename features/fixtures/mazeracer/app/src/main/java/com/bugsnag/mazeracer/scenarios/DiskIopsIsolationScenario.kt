package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.PerformanceTestUtils
import com.bugsnag.mazeracer.Scenario
import com.bugsnag.mazeracer.log

/**
 * ROAD 2233 Scenario 13: CPU and memory metrics remain present whether disk IOPS is on or off.
 *
 * Uses an app_session span so Android can assert min/max attrs (custom first-class spans
 * only emit CPU/memory means). Frozen-frame attrs need view frames and stay unit-tested.
 *
 * Do not [Thread.sleep] on the main thread for the sampler window — that blocks Maze command
 * handling and can prevent the ended span from being delivered. End + flush via [mainHandler].
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

        BugsnagPerformance.startAppSessionSpan(APP_SESSION_NAME)
        DiskIopsSupport.generateDiskActivity(context, "isolation")
        log(
            "DiskIopsIsolationScenario started app session " +
                "(disk=${config.enabledMetrics.disk}); ending after ${SAMPLER_DELAY_MS}ms",
        )

        // Allow AppSessionMetricsCollector (~1s interval) to take samples, then end on main.
        mainHandler.postDelayed({
            BugsnagPerformance.endAppSessionSpan()
            PerformanceTestUtils.flushBatch()
        }, SAMPLER_DELAY_MS)
    }

    private companion object {
        const val APP_SESSION_NAME = "DiskIopsIsolation"
        const val SAMPLER_DELAY_MS = 2500L
    }
}
