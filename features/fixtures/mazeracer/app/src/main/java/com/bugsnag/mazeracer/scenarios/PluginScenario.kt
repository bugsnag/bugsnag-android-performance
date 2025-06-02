package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.Plugin
import com.bugsnag.android.performance.PluginContext
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.controls.SpanControlProvider
import com.bugsnag.android.performance.controls.SpanQuery
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

        val span1 = BugsnagPerformance.startSpan("Span 1")
        val span2 = BugsnagPerformance.startSpan("Span 2")
        val span3 = BugsnagPerformance.startSpan("Span 3")

        val queriedSpan = BugsnagPerformance.getSpanControls(NumberedSpanQuery(2))
        queriedSpan?.setAttribute("queried", true)

        Thread.sleep(MINI_SLEEP)

        span1.end()
        span2.end()
        span3.end()
    }

    data class NumberedSpanQuery(val index: Int) : SpanQuery<Span>

    class SpanCounterPlugin : Plugin, SpanControlProvider<Span> {
        private val spanList = ArrayList<Span>()
        private var spanCount = 0

        override fun install(ctx: PluginContext) {
            ctx.addOnSpanStartCallback { span ->
                spanCount++
                span.setAttribute("spanCount", spanCount)
                spanList.add(span)
            }

            ctx.addSpanControlProvider(this)
        }

        override operator fun <Q : SpanQuery<Span>> get(query: Q): Span? {
            if (query is NumberedSpanQuery) {
                return spanList.getOrNull(query.index - 1)
            }

            return null
        }
    }
}
