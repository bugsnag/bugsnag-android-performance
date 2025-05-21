package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.Plugin
import com.bugsnag.android.performance.PluginContext
import com.bugsnag.mazeracer.Scenario

private const val MINI_SLEEP = 10L

class PluginScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        config.addPlugin(SpanCounterPlugin())
    }

    override fun startScenario() {
        BugsnagPerformance.start(config)

        BugsnagPerformance.startSpan("Span 1").use {
            // Simulate some work
            Thread.sleep(MINI_SLEEP)
        }

        BugsnagPerformance.startSpan("Span 2").use {
            // Simulate some work
            Thread.sleep(MINI_SLEEP)
        }

        BugsnagPerformance.startSpan("Span 3").use {
            // Simulate some work
            Thread.sleep(MINI_SLEEP)
        }
    }

    class SpanCounterPlugin : Plugin {
        private var spanCount = 0

        override fun install(context: PluginContext) {
            context.addOnSpanStartCallback { span ->
                spanCount++
                span.setAttribute("spanCount", spanCount)
            }
        }
    }
}
