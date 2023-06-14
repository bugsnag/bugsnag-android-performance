package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.mazeracer.ActivityViewLoadActivity
import com.bugsnag.mazeracer.Scenario

class ViewLoadLeakScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        BugsnagPerformance.start(config)
        startActivityAndFinish(
            ActivityViewLoadActivity.intent(
                context,
                config.autoInstrumentActivities,
                endViewLoadSpan = false
            ),
        )
    }
}
