package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.Scenario

class ThreeSpansScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 1
    }

    override fun startScenario() {
        BugsnagPerformance.start(config)
        Thread.sleep(300)
        BugsnagPerformance.startSpan("span 1").end()
        Thread.sleep(300)
        BugsnagPerformance.startSpan("span 2").end()
        Thread.sleep(300)
        BugsnagPerformance.startSpan("span 3").end()
    }
}
