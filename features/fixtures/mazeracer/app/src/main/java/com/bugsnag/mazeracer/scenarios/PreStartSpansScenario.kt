package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.android.performance.measureSpan
import com.bugsnag.mazeracer.Scenario
import kotlin.concurrent.thread

class PreStartSpansScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 4
    }

    override fun startScenario() {
        repeat(3) { index ->
            // these should each be queued for sending once we call 'start'
            thread {
                measureSpan("Thread Span $index") {
                    Thread.sleep(30)
                }
            }
        }

        Thread.sleep(100)
        BugsnagPerformance.start(config)

        measureSpan("Post Start") {
            Thread.sleep(100)
        }

        Thread.sleep(50)
    }
}
