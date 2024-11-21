package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.Scenario
import com.bugsnag.mazeracer.saveStartupConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class AppStartScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.workerSleepMs = 5000L
    }

    override fun startScenario() {
        config.autoInstrumentAppStarts = true
        config.autoInstrumentActivities = AutoInstrument.FULL

        launch {
            context.saveStartupConfig(config)

            delay(500L)
            // quit the app
            exitProcess(0)
        }
    }
}
