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

        val rStart = scenarioConfig["R Start"]?.toLongOrNull()
        val wStart = scenarioConfig["W Start"]?.toLongOrNull()
        val rEnd = scenarioConfig["R End"]?.toLongOrNull()
        val wEnd = scenarioConfig["W End"]?.toLongOrNull()
        val duration = scenarioConfig["Duration Sec"]?.toDoubleOrNull()

        runAndFlush {
            if (diskRead > 0 || diskWrite > 0) {
                BugsnagPerformance.startSpan("DiskMetricsMock").use { span ->
                    span.setAttribute("bugsnag.system.disk.read_bytes", diskRead)
                    span.setAttribute("bugsnag.system.disk.write_bytes", diskWrite)
                }
            }

            if (rStart != null && wStart != null && rEnd != null && wEnd != null && duration != null) {
                val platform = scenarioConfig["Platform"] ?: "android"
                val divisor = if (platform.equals("ios", ignoreCase = true)) 16384.0 else 1.0

                val iopsRead = if (duration > 0) (rEnd - rStart) / divisor / duration else 0.0
                val iopsWrite = if (duration > 0) (wEnd - wStart) / divisor / duration else 0.0
                BugsnagPerformance.startSpan("DiskIopsMock").use { span ->
                    // SDK emits all three disk IOPS attributes as IntValue (Long internally)
                    span.setAttribute("bugsnag.device.disk.iops_read", iopsRead.roundToLong())
                    span.setAttribute("bugsnag.device.disk.iops_write", iopsWrite.roundToLong())
                    span.setAttribute("bugsnag.device.disk.iops_total", (iopsRead + iopsWrite).roundToLong())
                }
            }

            measureSpan("ManualSpanScenario") {
                Thread.sleep(100L)
            }
        }
    }
}
