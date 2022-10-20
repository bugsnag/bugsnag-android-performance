package com.bugsnag.android.performance

import android.content.Context

class PerformanceConfiguration(val context: Context) {

    var apiKey: String? = null

    var endpoint: String = "https://localhost:8888/performance"

    var autoInstrumentAppStarts = true

    var autoInstrumentActivities = AutoInstrument.FULL

    override fun toString(): String =
        "PerformanceConfiguration(" +
            "context=$context, " +
            "apiKey=$apiKey, " +
            "endpoint='$endpoint', " +
            "autoInstrumentAppStarts=$autoInstrumentAppStarts, " +
            "autoInstrumentActivities=$autoInstrumentActivities" +
            ")"
}
