package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.mazeracer.Scenario
import com.bugsnag.mazeracer.saveStartupConfig
import kotlin.system.exitProcess

private const val PRE_EXIT_DELAY = 500L

class AppStartDisabledScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        config.autoInstrumentAppStarts = false
        config.autoInstrumentActivities = AutoInstrument.FULL
        context.saveStartupConfig(config)

        Thread.sleep(PRE_EXIT_DELAY)
        // quit the app
        exitProcess(0)
    }
}
