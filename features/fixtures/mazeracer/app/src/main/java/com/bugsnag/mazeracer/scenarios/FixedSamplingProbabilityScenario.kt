package com.bugsnag.mazeracer.scenarios

import android.util.Log
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.Scenario

class FixedSamplingProbabilityScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String
) : Scenario(config, scenarioMetadata) {

    init {
        config.samplingProbability = scenarioMetadata.toDoubleOrNull()
        InternalDebug.spanBatchSizeSendTriggerPoint = 1
        BugsnagPerformance.start(config)
    }

    override fun startScenario() {
        Log.i("FixedSamplingProbabilityScenario", "sendingASpan")
        BugsnagPerformance.startSpan("span").end()
    }
}
