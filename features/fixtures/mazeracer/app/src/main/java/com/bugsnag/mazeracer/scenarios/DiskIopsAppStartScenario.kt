package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.Scenario
import com.bugsnag.mazeracer.saveStartupConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.exitProcess

/**
 * Maze Runner scenario for ROAD 2233 Scenario 1 on app_start spans.
 * Enables disk metrics, persists config, and restarts so cold start is instrumented.
 */
class DiskIopsAppStartScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.workerSleepMs = WORKER_SLEEP_MS
    }

    override fun startScenario() {
        config.appSessionConfig.autoStartSession = false
        config.autoInstrumentAppStarts = true
        config.autoInstrumentActivities = AutoInstrument.FULL
        config.enabledMetrics.disk = true

        val realIo = File("/proc/self/io")
        if (!realIo.exists() || !realIo.canRead()) {
            val fakeIo = File(context.cacheDir, "fake_io_appstart")
            fakeIo.writeText(
                "syscr: $FAKE_SYSCR\nsyscw: $FAKE_SYSCW\n",
            )
            InternalDebug.procIoPath = fakeIo.absolutePath
        }

        launch {
            context.saveStartupConfig(config)

            delay(SAVE_AND_EXIT_DELAY_MS)
            exitProcess(0)
        }
    }

    private companion object {
        private const val WORKER_SLEEP_MS = 5000L
        private const val SAVE_AND_EXIT_DELAY_MS = 500L
        private const val FAKE_SYSCR = 1000L
        private const val FAKE_SYSCW = 500L
    }
}
