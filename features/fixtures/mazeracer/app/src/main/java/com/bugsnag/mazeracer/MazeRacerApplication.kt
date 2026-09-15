package com.bugsnag.mazeracer

import android.app.Application
import android.content.Context
import android.os.StrictMode
import android.util.Log
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.internal.InternalDebug
import java.io.File

class MazeRacerApplication : Application() {
    init {
        instance = this
        Log.i("MazeRacer", "MazeRacerApplication static init")
    }

    companion object {
        private var instance: MazeRacerApplication? = null

        fun applicationContext(): Context {
            return instance!!.applicationContext
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // We must initialize procIoPath as early as possible (before ContentProviders run)
        // so that the auto-instrumented AppStart span uses the correct file.
        val prefs = getSharedPreferences("StartupConfig", Context.MODE_PRIVATE)
        if (prefs.getBoolean("configured", false)) {
            val savedPath = prefs.getString("procIoPath", "/proc/self/io")
            if (savedPath != null) {
                InternalDebug.procIoPath = savedPath
                Log.i("MazeRacer", "Early init procIoPath: $savedPath")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // if there is stored "startup" config then we start BugsnagPerformance before the scenario
        // this is used to test things like app-start instrumentation
        readStartupConfig()?.let { config ->
            InternalDebug.workerSleepMs = WORKER_SLEEP_MS
            // Advance fake counters to ensure delta > 0 for AppStart on restricted devices
            if (InternalDebug.procIoPath.contains("fake_io")) {
                val file = File(InternalDebug.procIoPath)
                // Start counters
                file.writeText(
                    "syscr: $FAKE_SYSCR_START\nsyscw: $FAKE_SYSCW_START\n",
                )
                // Update file again after a small delay so endMetrics sees higher values
                Thread {
                    try {
                        Thread.sleep(FAKE_COUNTER_UPDATE_DELAY_MS)
                        file.writeText(
                            "syscr: $FAKE_SYSCR_END\nsyscw: $FAKE_SYSCW_END\n",
                        )
                    } catch (_: Exception) {
                    }
                }.start()
            }

            BugsnagPerformance.start(config)
        }

        BugsnagPerformance.reportApplicationClassLoaded()

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectNetwork()
                .penaltyLog()
                .penaltyDeath()
                .build(),
        )
    }

    private companion object {
        private const val WORKER_SLEEP_MS = 2000L
        private const val FAKE_COUNTER_UPDATE_DELAY_MS = 300L

        private const val FAKE_SYSCR_START = 500L
        private const val FAKE_SYSCW_START = 250L

        private const val FAKE_SYSCR_END = 1500L
        private const val FAKE_SYSCW_END = 750L
    }
}
