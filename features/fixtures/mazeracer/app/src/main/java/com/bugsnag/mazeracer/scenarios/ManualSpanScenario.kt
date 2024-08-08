package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.measureSpan
import com.bugsnag.android.performance.SpanEndCallback
import com.bugsnag.mazeracer.Scenario

class ManualSpanScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        config.addOnSpanEndCallback { span ->
            span as SpanImpl
            span.setAttribute("bigNumber", 1234L)
            span.setAttribute("list", listOf("string1", "string2", "string3"))
            true
        }
        BugsnagPerformance.start(config)
        runAndFlush {
            measureSpan("ManualSpanScenario") {
                Thread.sleep(100L)
            }
        }
    }
}
