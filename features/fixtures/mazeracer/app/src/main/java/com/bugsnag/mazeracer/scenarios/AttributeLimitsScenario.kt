package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.mazeracer.Scenario

class AttributeLimitsScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    @Suppress("MagicNumber")
    override fun startScenario() {
        config.attributeCountLimit = 1
        config.attributeStringValueLimit = 10
        config.attributeArrayLengthLimit = 1

        BugsnagPerformance.start(config)

        runAndFlush {
            BugsnagPerformance.startSpan("Custom Span").use { span ->
                span.setAttribute(
                    "arrayAttribute",
                    arrayOf(
                        "this is a really long string inside an array, and the string will be truncated",
                        "this is an extra array element which will be dropped",
                    ),
                )

                span.setAttribute("droppedAttribute", "this attribute will be dropped")

                Thread.sleep(100L)
            }
        }
    }
}
