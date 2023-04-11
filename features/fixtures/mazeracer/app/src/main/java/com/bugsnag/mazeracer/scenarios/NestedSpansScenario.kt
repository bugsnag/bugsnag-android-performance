package com.bugsnag.mazeracer.scenarios

import android.content.Intent
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.NestedSpansActivity
import com.bugsnag.mazeracer.Scenario

const val BATCH_SIZE = 10

class NestedSpansScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = BATCH_SIZE
        config.autoInstrumentAppStarts = true
        config.autoInstrumentActivities = AutoInstrument.FULL
    }

    override fun startScenario() {
        BugsnagPerformance.start(config)
        context.startActivity(Intent(context, NestedSpansActivity::class.java))
    }
}
