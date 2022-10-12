package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.measureSpan
import com.bugsnag.mazeracer.Scenario

class ManualSpanScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        BugsnagPerformance.start(config)
        measureSpan("ManualSpanScenario") {
            Thread.sleep(100L)
        }
    }
}
