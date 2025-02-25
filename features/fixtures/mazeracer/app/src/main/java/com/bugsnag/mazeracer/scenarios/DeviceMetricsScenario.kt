package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.EnabledMetrics
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.SpanMetrics
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.mazeracer.Scenario

private const val TEST_SPAN_TIME = 10L
private const val SAMPLER_TIMEOUT = 2000L

class DeviceMetricsScenario(config: PerformanceConfiguration, scenarioMetadata: String) :
    Scenario(config, scenarioMetadata) {
    init {
        var metrics = EnabledMetrics(false)

        if (scenarioMetadata.contains("all")) {
            metrics = EnabledMetrics(true)
        }

        if (scenarioMetadata.contains("cpu")) {
            metrics.cpu = true
        }

        if (scenarioMetadata.contains("memory")) {
            metrics.memory = true
        }

        config.enabledMetrics = metrics
    }

    override fun startScenario() {
        BugsnagPerformance.start(config)

        runAndFlush {
            val firstClass = SpanOptions.setFirstClass(true)
            val notFirstClass = SpanOptions.setFirstClass(false)
            BugsnagPerformance.startSpan("FirstClass", firstClass).use {
                Thread.sleep(TEST_SPAN_TIME)
            }

            BugsnagPerformance.startSpan("Not FirstClass", notFirstClass).use {
                Thread.sleep(TEST_SPAN_TIME)
            }

            BugsnagPerformance.startSpan("No Metrics", notFirstClass.withMetrics(null)).use {
                Thread.sleep(TEST_SPAN_TIME)
            }

            BugsnagPerformance.startSpan(
                "CPU Metrics Only",
                notFirstClass.withMetrics(
                    SpanMetrics(
                        cpu = true,
                        rendering = false,
                        memory = false,
                    ),
                ),
            ).use {
                Thread.sleep(TEST_SPAN_TIME)
            }

            BugsnagPerformance.startSpan(
                "Memory Metrics Only",
                notFirstClass.withMetrics(
                    SpanMetrics(
                        cpu = false,
                        rendering = false,
                        memory = true,
                    ),
                ),
            ).use {
                Thread.sleep(TEST_SPAN_TIME)
            }

            // give enough time for the samplers to add the end metrics
            Thread.sleep(SAMPLER_TIMEOUT)
        }
    }
}
