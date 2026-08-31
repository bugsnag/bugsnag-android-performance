package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.Scenario
import com.bugsnag.mazeracer.saveStartupConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

/**
 * Maze Runner scenario for ROAD 2233 Scenario 1 on app_start spans.
 * Enables disk metrics, persists config, and restarts so cold start is instrumented.
 */
class DiskIopsAppStartScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.workerSleepMs = 5000L
    }

    override fun startScenario() {
        config.appSessionConfig.autoStartSession = false
        config.autoInstrumentAppStarts = true
        config.autoInstrumentActivities = AutoInstrument.FULL
        config.enabledMetrics.disk = true

        launch {
            context.saveStartupConfig(config)

            delay(500L)
            exitProcess(0)
        }
    }
}
