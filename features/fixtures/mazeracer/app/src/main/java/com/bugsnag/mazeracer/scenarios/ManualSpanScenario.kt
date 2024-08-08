package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.measureSpan
import com.bugsnag.android.performance.SpanEndCallback
import com.bugsnag.mazeracer.Scenario

class ManualSpanScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        config.serviceName = "manual.span.service"
    }

    override fun startScenario() {
        config.addOnSpanEndCallback { span ->
            span.setAttribute("string", "test name")
            span.setAttribute("longNumber", 1234L)
            span.setAttribute("intNumber", 5678)
            span.setAttribute("doubleNumber", 12.34)
            span.setAttribute("boolean", false)
            span.setAttribute("stringCollection", listOf("string1", "string2", "string3"))
            span.setAttribute("intArray", intArrayOf(10, 20, 30, 40))
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
