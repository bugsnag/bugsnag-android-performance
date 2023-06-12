package com.bugsnag.mazeracer.scenarios

import android.os.Build
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.ActivityViewLoadActivity
import com.bugsnag.mazeracer.Scenario

class ActivityLoadInstrumentationScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> 2
            else -> 5
        }

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
