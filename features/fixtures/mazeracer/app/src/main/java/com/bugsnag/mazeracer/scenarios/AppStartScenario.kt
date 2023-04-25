package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.Scenario
import com.bugsnag.mazeracer.saveStartupConfig
import kotlin.system.exitProcess

class AppStartScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 5
        InternalDebug.workerSleepMs = 1000L
    }

    override fun startScenario() {
        config.autoInstrumentAppStarts = true
        config.autoInstrumentActivities = AutoInstrument.FULL
        context.saveStartupConfig(config)

        Thread.sleep(500L)
        // quit the app
        exitProcess(0)
    }
}
