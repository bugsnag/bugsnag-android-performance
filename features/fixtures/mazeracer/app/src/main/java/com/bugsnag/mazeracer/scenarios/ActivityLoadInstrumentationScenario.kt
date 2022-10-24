package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.mazeracer.ActivityViewLoadActivity
import com.bugsnag.mazeracer.Scenario

class ActivityLoadInstrumentationScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String
) : Scenario(config, scenarioMetadata) {
    init {
        config.autoInstrumentActivities =
            scenarioMetadata.takeIf { it.isNotBlank() }
            ?.let { AutoInstrument.valueOf(it) }
            ?: AutoInstrument.FULL
    }

    override fun startScenario() {
        BugsnagPerformance.start(config)
        context.startActivity(
            ActivityViewLoadActivity.intent(context, config.autoInstrumentActivities)
        )
    }
}
