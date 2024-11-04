package com.bugsnag.mazeracer.scenarios

import android.content.Intent
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.mazeracer.AnimatedActivity
import com.bugsnag.mazeracer.Scenario

class FrameMetricsScenario(config: PerformanceConfiguration, scenarioMetadata: String) :
    Scenario(config, scenarioMetadata) {
    init {
        if (scenarioMetadata.contains("disableInstrumentation")) {
            config.autoInstrumentRendering = false
        } else {
            config.autoInstrumentRendering = true
        }
    }

    override fun startScenario() {
        BugsnagPerformance.start(config)
        startActivityAndFinish(Intent(context, AnimatedActivity::class.java))
    }
}
