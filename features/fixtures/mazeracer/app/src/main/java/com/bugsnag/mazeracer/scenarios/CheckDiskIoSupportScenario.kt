package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.mazeracer.Scenario
import java.io.File

class CheckDiskIoSupportScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        BugsnagPerformance.start(config)
        forceConfigureMetrics(config.enabledMetrics)
        runAndFlush {
            val ioFile = File("/proc/self/io")
            val exists = ioFile.exists()
            val content = if (exists) ioFile.readText() else "MISSING"
            
            BugsnagPerformance.startSpan("CheckDiskIoSupport").use { span ->
                span.setAttribute("io_file_exists", exists)
                span.setAttribute("io_file_content", content)
            }
        }
    }
}
