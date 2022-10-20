package com.bugsnag.mazeracer

import android.content.Context
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.PerformanceConfiguration

fun Context.saveStartupConfig(config: PerformanceConfiguration) {
    log("saveStartupConfig: $config")

    applicationContext.getSharedPreferences("StartupConfig", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("configured", true)
        .putString("apiKey", config.apiKey)
        .putString("endpoint", config.endpoint)
        .putBoolean("autoInstrumentAppStarts", config.autoInstrumentAppStarts)
        .putString("autoInstrumentActivities", config.autoInstrumentActivities.name)
        .commit()
}

fun Context.readStartupConfig(): PerformanceConfiguration? {
    log("readStartupConfig()")

    val prefs = applicationContext.getSharedPreferences("StartupConfig", Context.MODE_PRIVATE)
    try {
        if (!prefs.getBoolean("configured", false)) {
            log("readStartupConfig, configured=false: $prefs")
            return null
        }

        val config = PerformanceConfiguration(this).apply {
            apiKey = prefs.getString("apiKey", "a35a2a72bd230ac0aa0f52715bbdc6aa")
            endpoint = prefs.getString("endpoint", null)!!
            autoInstrumentAppStarts = prefs.getBoolean("autoInstrumentAppStarts", false)
            autoInstrumentActivities = AutoInstrument.valueOf(
                prefs.getString("autoInstrumentActivities", "OFF")!!
            )
        }

        log("got some config Dave: $config")

        return config
    } finally {
        // make sure we don't leave this config around for the next startup
        prefs.edit()
            .putBoolean("configured", false)
            .remove("apiKey")
            .remove("endpoint")
            .remove("autoInstrumentAppStarts")
            .remove("autoInstrumentActivities")
            .commit()
    }
}
