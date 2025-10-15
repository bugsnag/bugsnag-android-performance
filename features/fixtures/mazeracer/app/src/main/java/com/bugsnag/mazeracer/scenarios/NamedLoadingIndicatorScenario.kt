package com.bugsnag.mazeracer.scenarios

import android.content.Intent
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.mazeracer.NamedLoadingIndicatorViewActivity
import com.bugsnag.mazeracer.Scenario

class NamedLoadingIndicatorScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        config.autoInstrumentActivities = AutoInstrument.FULL
    }

    override fun startScenario() {
        BugsnagPerformance.start(config)

        startActivityAndFinish(
            Intent(context, NamedLoadingIndicatorViewActivity::class.java),
        )
    }
}
