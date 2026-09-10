package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.SpanMetrics
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.Scenario
import java.io.File

class DiskIopsScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 1
        InternalDebug.attachDiskIoSnapshots = true
        config.appSessionConfig.autoStartSession = false
        config.enabledMetrics.disk = true
    }

    override fun startScenario() {
        val type = scenarioConfig["span_type"] ?: scenarioMetadata
        InternalDebug.diskIoTimestampFault = scenarioConfig["duration_fault"].orEmpty()

        // If real /proc/self/io is blocked, we use a fake one in the fixture to ensure
        // that we can still test the SDK's internal IOPS logic on restricted devices.
        val realIo = File("/proc/self/io")
        if (!realIo.exists() || !realIo.canRead()) {
            val fakeIo = File(context.cacheDir, "fake_io")
            fakeIo.writeText("syscr: 100\nsyscw: 50\n")
            InternalDebug.procIoPath = fakeIo.absolutePath
        }

        BugsnagPerformance.start(config)

        forceConfigureMetrics(config.enabledMetrics)

        runAndFlush {
            if (type == "custom") {
                val span = BugsnagPerformance.startSpan(
                    "DiskIopsCustom",
                    SpanOptions.withMetrics(SpanMetrics(disk = true)),
                )
                // Do some I/O to ensure deltas can be > 0 if using real file
                if (InternalDebug.procIoPath == "/proc/self/io") {
                    File(context.cacheDir, "test_custom.txt").writeText("some data to force disk I/O")
                } else {
                    // Update fake file to simulate I/O
                    File(InternalDebug.procIoPath).writeText("syscr: 130\nsyscw: 65\n")
                }
                Thread.sleep(200L) // Ensure duration > 0
                span.end()
            } else if (type == "app_session") {
                BugsnagPerformance.startAppSessionSpan("DiskIops")
                if (InternalDebug.procIoPath == "/proc/self/io") {
                    File(context.cacheDir, "test_session.txt").writeText("session data")
                } else {
                    File(InternalDebug.procIoPath).writeText("syscr: 140\nsyscw: 70\n")
                }
                Thread.sleep(200L)
                BugsnagPerformance.endAppSessionSpan()
            }
        }
    }
}
