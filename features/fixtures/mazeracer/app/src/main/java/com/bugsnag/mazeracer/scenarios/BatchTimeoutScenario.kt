package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.android.performance.measureSpan
import com.bugsnag.mazeracer.Scenario

class BatchTimeoutScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 100
        InternalDebug.workerSleepMs = 250
    }

    override fun startScenario() {
        BugsnagPerformance.start(config)

        // a short sleep to allow the worker to finish a single pass before continuing
        Thread.sleep(50)

        measureSpan("Span 1") {
            Thread.sleep(30)
        }

        measureSpan("Span 2") {
            Thread.sleep(30)
        }

        Thread.sleep(InternalDebug.workerSleepMs)

        measureSpan("Span 3") {
            Thread.sleep(30)
        }

        Thread.sleep(30)
    }
}
