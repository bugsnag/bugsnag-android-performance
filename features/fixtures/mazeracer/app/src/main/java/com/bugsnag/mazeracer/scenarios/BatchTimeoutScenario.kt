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
        InternalDebug.spanBatchTimeout = 100
    }

    override fun startScenario() {
        BugsnagPerformance.start(config)

        measureSpan("Span 1") {
            Thread.sleep(30)
        }

        measureSpan("Span 2") {
            Thread.sleep(30)
        }

        Thread.sleep(100)

        measureSpan("Span 3") {
            Thread.sleep(30)
        }

        Thread.sleep(30)
    }
}
