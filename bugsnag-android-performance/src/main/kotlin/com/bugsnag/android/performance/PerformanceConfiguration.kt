package com.bugsnag.android.performance

import android.content.Context
import androidx.annotation.FloatRange

class PerformanceConfiguration(val context: Context) {

    var apiKey: String? = null

    var endpoint: String = "https://otlp.bugsnag.com/v1/traces"

    var autoInstrumentAppStarts = true

    var autoInstrumentActivities = AutoInstrument.FULL

    var releaseStage: String? = null

    var versionCode: Long? = null

    @FloatRange(from = 0.0, to = 1.0)
    var samplingProbability: Double = 1.0
        set(value) {
            require(field in 0.0..1.0) { "samplingProbability out of range (0..1): $value" }
            field = value
        }

    override fun toString(): String =
        "PerformanceConfiguration(" +
            "context=$context, " +
            "apiKey=$apiKey, " +
            "endpoint='$endpoint', " +
            "autoInstrumentAppStarts=$autoInstrumentAppStarts, " +
            "autoInstrumentActivities=$autoInstrumentActivities" +
            "samplingProbability=$samplingProbability" +
            ")"
}
