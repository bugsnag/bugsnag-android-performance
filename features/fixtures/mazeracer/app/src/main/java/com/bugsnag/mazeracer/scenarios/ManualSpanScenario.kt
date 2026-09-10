package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.measureSpan
import com.bugsnag.mazeracer.Scenario
import kotlin.math.roundToLong

class ManualSpanScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        config.serviceName = "manual.span.service"

        config.addOnSpanStartCallback { span ->
            span.setAttribute("spanStartCallback", true)
        }

        config.addOnSpanEndCallback { span ->
            span.setAttribute("spanEndCallback", true)
            true
        }
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

        val diskRead = scenarioConfig["disk_read_bytes"]?.toLongOrNull() ?: 0L
        val diskWrite = scenarioConfig["disk_write_bytes"]?.toLongOrNull() ?: 0L
        runAndFlush {
            if (diskRead > 0 || diskWrite > 0) {
                BugsnagPerformance.startSpan("DiskMetricsMock").use { span ->
                    span.setAttribute("bugsnag.system.disk.read_bytes", diskRead)
                    span.setAttribute("bugsnag.system.disk.write_bytes", diskWrite)
                }
            }

            measureSpan("ManualSpanScenario") {
                Thread.sleep(100L)
            }
        }
    }
}
