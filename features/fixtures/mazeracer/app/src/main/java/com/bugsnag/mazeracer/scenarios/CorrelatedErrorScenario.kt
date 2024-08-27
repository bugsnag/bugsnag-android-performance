package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.Bugsnag
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.Scenario

class CorrelatedErrorScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {

    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 1
        BugsnagPerformance.start(config)
    }

    override fun startScenario() {
        BugsnagPerformance.startSpan("CorrelatedError Span").use {
            Bugsnag.notify(RuntimeException("this is an error"))
        }
    }
}
