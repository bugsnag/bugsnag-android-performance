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
            fakeIo.writeText(
                "syscr: $FAKE_SYSCR_INITIAL\nsyscw: $FAKE_SYSCW_INITIAL\n",
            )
            InternalDebug.procIoPath = fakeIo.absolutePath
        }

        BugsnagPerformance.start(config)

        forceConfigureMetrics(config.enabledMetrics)

        runAndFlush {
            if (type == "custom") {
                val span =
                    BugsnagPerformance.startSpan(
                        "DiskIopsCustom",
                        SpanOptions.withMetrics(SpanMetrics(disk = true)),
                    )
                // Do some I/O to ensure deltas can be > 0 if using real file
                if (InternalDebug.procIoPath == "/proc/self/io") {
                    File(context.cacheDir, "test_custom.txt").writeText("some data to force disk I/O")
                } else {
                    // Update fake file to simulate I/O
                    File(InternalDebug.procIoPath).writeText(
                        "syscr: $FAKE_SYSCR_CUSTOM\nsyscw: $FAKE_SYSCW_CUSTOM\n",
                    )
                }
                Thread.sleep(SPAN_END_DELAY_MS) // Ensure duration > 0
                span.end()
            } else if (type == "app_session") {
                BugsnagPerformance.startAppSessionSpan("DiskIops")
                if (InternalDebug.procIoPath == "/proc/self/io") {
                    File(context.cacheDir, "test_session.txt").writeText("session data")
                } else {
                    File(InternalDebug.procIoPath).writeText(
                        "syscr: $FAKE_SYSCR_SESSION\nsyscw: $FAKE_SYSCW_SESSION\n",
                    )
                }
                Thread.sleep(SPAN_END_DELAY_MS)
                BugsnagPerformance.endAppSessionSpan()
            }
        }
    }

    private companion object {
        private const val SPAN_END_DELAY_MS = 200L

        private const val FAKE_SYSCR_INITIAL = 100L
        private const val FAKE_SYSCW_INITIAL = 50L

        private const val FAKE_SYSCR_CUSTOM = 130L
        private const val FAKE_SYSCW_CUSTOM = 65L

        private const val FAKE_SYSCR_SESSION = 140L
        private const val FAKE_SYSCW_SESSION = 70L
    }
}
