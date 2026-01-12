package com.bugsnag.mazeracer

import android.content.Context
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.PerformanceConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun Context.saveStartupConfig(config: PerformanceConfiguration) =
    withContext(Dispatchers.IO) {
        log("saveStartupConfig: $config")

        applicationContext.getSharedPreferences("StartupConfig", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("configured", true)
            .putString("apiKey", config.apiKey)
            .putString("endpoint", config.endpoint)
            .putBoolean("autoInstrumentAppStarts", config.autoInstrumentAppStarts)
            .putString("autoInstrumentActivities", config.autoInstrumentActivities.name)
            .putBoolean("cpuMetrics", config.enabledMetrics.cpu)
            .putBoolean("memoryMetrics", config.enabledMetrics.memory)
            .putBoolean("renderingMetrics", config.enabledMetrics.rendering)
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

        val config =
            PerformanceConfiguration
                .load(this, prefs.getString("apiKey", "a35a2a72bd230ac0aa0f52715bbdc6aa"))
                .apply {
                    endpoint = prefs.getString("endpoint", null)!!
                    autoInstrumentAppStarts = prefs.getBoolean("autoInstrumentAppStarts", false)
                    autoInstrumentActivities =
                        AutoInstrument.valueOf(
                            prefs.getString("autoInstrumentActivities", "OFF")!!,
                        )
                    enabledMetrics.cpu = prefs.getBoolean("cpuMetrics", false)
                    enabledMetrics.memory = prefs.getBoolean("memoryMetrics", false)
                    enabledMetrics.rendering = prefs.getBoolean("renderingMetrics", false)
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
            .remove("cpuMetrics")
            .remove("memoryMetrics")
            .remove("renderingMetrics")
            .apply()
    }
}
