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
            InternalDebug.workerSleepMs = 2000L
            
            // Advance fake counters to ensure delta > 0 for AppStart on restricted devices
            if (InternalDebug.procIoPath.contains("fake_io")) {
                val file = File(InternalDebug.procIoPath)
                // Start counters
                file.writeText("syscr: 500\nsyscw: 250\n")
                
                // Update file again after a small delay so endMetrics sees higher values
                Thread {
                    try {
                        Thread.sleep(300L)
                        file.writeText("syscr: 1500\nsyscw: 750\n")
                    } catch (_: Exception) {}
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
}
