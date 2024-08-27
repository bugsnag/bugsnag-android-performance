package com.bugsnag.mazeracer.scenarios

import android.util.Log
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.Scenario

class GenerateSpansScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String
) : Scenario(config, scenarioMetadata) {

    var spanId = 1

    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 1
        BugsnagPerformance.start(config)
    }

    override fun startScenario() {
        // not used by this scenario, which is driven by "invoke" commands
    }

    fun sendNextSpan() {
        Log.i("GenerateSpansScenario", "sendNextSpan($spanId)")
        BugsnagPerformance.startSpan("span $spanId").end()
        spanId++
    }
}
