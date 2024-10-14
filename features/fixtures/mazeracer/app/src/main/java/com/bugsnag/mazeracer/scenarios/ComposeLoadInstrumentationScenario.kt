package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.mazeracer.ComposeViewLoadActivity
import com.bugsnag.mazeracer.Scenario

class ComposeLoadInstrumentationScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        BugsnagPerformance.start(config)
        startActivityAndFinish(ComposeViewLoadActivity.intent(context))
    }
}
