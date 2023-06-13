package com.bugsnag.mazeracer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.SpanOptions

class MazeRacerAlarmReceiver : BroadcastReceiver() {
    init {
        Log.i("MazeRacer", "MazeRacerAlarmReceiver init")
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i("MazeRacer", "MazeRacerAlarmReceiver.onReceive()")
        BugsnagPerformance.startSpan("AlarmReceiver", SpanOptions.DEFAULTS.setFirstClass(true)).use {
            Thread.sleep(250L)
        }
    }
}
