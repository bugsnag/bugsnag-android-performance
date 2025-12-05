package com.bugsnag.mazeracer

import android.app.Application
import android.content.Context
import android.os.StrictMode
import android.util.Log
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.debug.UnclosedSpansTracker

class MazeRacerApplication : Application() {
    init {
        instance = this
        SpanContext.defaultStorage = UnclosedSpansTracker
        BugsnagPerformance.reportApplicationClassLoaded()
        log("MazeRacerApplication static init")
    }

    companion object {
        private var instance: MazeRacerApplication? = null

        fun applicationContext(): Context {
            return instance!!.applicationContext
        }
    }

    override fun onCreate() {
        super.onCreate()

        // if there is stored "startup" config then we start BugsnagPerformance before the scenario
        // this is used to test things like app-start instrumentation
        readStartupConfig()?.let { config ->
            InternalDebug.workerSleepMs = 2000L
            config.addOnSpanStartCallback(UnclosedSpansTracker)
            config.addOnSpanEndCallback(UnclosedSpansTracker)
            BugsnagPerformance.start(config)
        }

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectNetwork()
                .penaltyLog()
                .penaltyDeath()
                .build(),
        )
    }
}
