package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.android.performance.measureSpan
import com.bugsnag.mazeracer.Scenario

class AppBackgroundedScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 100
        // this should be longer than the Mazerunner timeout
        InternalDebug.workerSleepMs = 90_000L
    }

    override fun startScenario() {
        BugsnagPerformance.start(config)

        measureSpan("Span 1") {
            Thread.sleep(1)
        }
    }
}
