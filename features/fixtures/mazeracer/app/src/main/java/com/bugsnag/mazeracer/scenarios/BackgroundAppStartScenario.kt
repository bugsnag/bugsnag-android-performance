package com.bugsnag.mazeracer.scenarios

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.mazeracer.MazeRacerAlarmReceiver
import com.bugsnag.mazeracer.Scenario
import com.bugsnag.mazeracer.saveStartupConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class BackgroundAppStartScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        config.autoInstrumentAppStarts = true
        config.autoInstrumentActivities = AutoInstrument.FULL

        launch {
            context.saveStartupConfig(config)

            // ask for the entire app to be woken up in 1 second, but with a broadcast (not activity)
            val alarmService = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmService.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1000L,
                PendingIntent.getBroadcast(
                    context,
                    100,
                    Intent(context, MazeRacerAlarmReceiver::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )

            delay(100L)
            // quit the app to allow for a full restart
            exitProcess(0)
        }
    }
}
