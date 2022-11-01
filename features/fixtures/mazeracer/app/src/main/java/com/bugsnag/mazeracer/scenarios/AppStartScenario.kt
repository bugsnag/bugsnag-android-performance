package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.mazeracer.Scenario
import com.bugsnag.mazeracer.saveStartupConfig
import kotlin.system.exitProcess

class AppStartScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        config.autoInstrumentAppStarts = true
        config.autoInstrumentActivities = AutoInstrument.OFF
        context.saveStartupConfig(config)

        // quit the app
        exitProcess(0)
    }
}
