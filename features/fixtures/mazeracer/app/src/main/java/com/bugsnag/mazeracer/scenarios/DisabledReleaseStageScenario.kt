package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.Scenario

class DisabledReleaseStageScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.workerSleepMs = 500L
        config.enabledReleaseStages = setOf("staging")
    }

    override fun startScenario() {
        BugsnagPerformance.start(config)

        BugsnagPerformance.startSpan("Custom Span").use {
            Thread.sleep(100L)
        }
    }
}
