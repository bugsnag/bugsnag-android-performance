package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.mazeracer.Scenario
import kotlin.concurrent.thread

class PreStartSpansScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        config.autoInstrumentAppStarts = true
    }

    override fun startScenario() {
        repeat(3) { index ->
            // these should each be queued for sending once we call 'start'
            thread {
                BugsnagPerformance.startSpan("Thread Span $index", SpanOptions.setFirstClass(false))
                    .use {
                        Thread.sleep(30)
                    }
            }
        }

        Thread.sleep(100)
        BugsnagPerformance.start(config)

        runAndFlush {
            BugsnagPerformance.startSpan("Post Start", SpanOptions.setFirstClass(false)).use {
                Thread.sleep(100)
            }

            Thread.sleep(50)
        }
    }
}
