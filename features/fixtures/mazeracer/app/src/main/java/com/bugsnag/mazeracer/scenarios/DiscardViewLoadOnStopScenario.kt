package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.mazeracer.ActivityViewLoadActivity
import com.bugsnag.mazeracer.Scenario

class DiscardViewLoadOnStopScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        config.autoInstrumentAppStarts = false
        config.autoInstrumentActivities = AutoInstrument.START_ONLY
    }

    override fun startScenario() {
        BugsnagPerformance.start(config)
        startActivityAndFinish(
            ActivityViewLoadActivity.intent(
                context,
                config.autoInstrumentActivities,
            ),
        )
    }
}
