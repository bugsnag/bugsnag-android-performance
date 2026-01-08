package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.FragmentInstrumentation
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.mazeracer.ActivityViewLoadActivity
import com.bugsnag.mazeracer.Scenario

class FragmentInstrumentationDisabledScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        FragmentInstrumentation.enabled = false
        config.autoInstrumentActivities =
            scenarioMetadata.takeIf { it.isNotBlank() }
                ?.let { AutoInstrument.valueOf(it) }
                ?: AutoInstrument.FULL
    }

    override fun startScenario() {
        BugsnagPerformance.start(config)
        startActivityAndFinish(
            ActivityViewLoadActivity.intent(
                context,
                config.autoInstrumentActivities,
            ),
        )
    }
}
