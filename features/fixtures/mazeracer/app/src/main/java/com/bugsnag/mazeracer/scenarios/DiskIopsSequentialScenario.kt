package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.Scenario
import java.io.File

/**
 * ROAD 2233: consecutive spans each capture a fresh disk snapshot (no stale start counters).
 */
class DiskIopsSequentialScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    private var fakeSyscr = FAKE_SYSCR_INITIAL
    private var fakeSyscw = FAKE_SYSCW_INITIAL

    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 2
        InternalDebug.attachDiskIoSnapshots = true
        config.appSessionConfig.autoStartSession = false
        config.enabledMetrics.disk = true
    }

    override fun startScenario() {
        DiskIopsSupport.ensureProcIoReadable(context)
        BugsnagPerformance.start(config)
        forceConfigureMetrics(config.enabledMetrics)

        runAndFlush {
            runSequentialSpan("DiskIopsSequential1", "seq1")
            runSequentialSpan("DiskIopsSequential2", "seq2")
        }
    }

    private fun runSequentialSpan(
        name: String,
        label: String,
    ) {
        val span =
            BugsnagPerformance.startSpan(
                name,
                DiskIopsSupport.diskSpanOptions(disk = true),
            )
        generateActivity(label)
        Thread.sleep(DiskIopsSupport.SPAN_SLEEP_MS)
        span.end()
    }

    private fun generateActivity(label: String) {
        if (InternalDebug.procIoPath == "/proc/self/io") {
            File(context.cacheDir, "$label.txt").writeText("data-$label-${System.nanoTime()}")
        } else {
            // Keep fake /proc counters monotonic so sequential spans never see regressing ends.
            fakeSyscr += FAKE_SYSCR_DELTA
            fakeSyscw += FAKE_SYSCW_DELTA
            File(InternalDebug.procIoPath).writeText("syscr: $fakeSyscr\nsyscw: $fakeSyscw\n")
        }
    }

    private companion object {
        private const val FAKE_SYSCR_INITIAL = 100L
        private const val FAKE_SYSCW_INITIAL = 50L
        private const val FAKE_SYSCR_DELTA = 30L
        private const val FAKE_SYSCW_DELTA = 15L
    }
}
