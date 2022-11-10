package com.bugsnag.mazeracer.scenarios

import android.content.ComponentCallbacks2
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.measureSpan
import com.bugsnag.mazeracer.Scenario

class AppBackgroundedScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        BugsnagPerformance.start(config)

        measureSpan("Span 1") {
            Thread.sleep(1)
        }

        BugsnagPerformance.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)

        measureSpan("Span 2") {
            Thread.sleep(1)
        }

        Thread.sleep(10)
    }
}
