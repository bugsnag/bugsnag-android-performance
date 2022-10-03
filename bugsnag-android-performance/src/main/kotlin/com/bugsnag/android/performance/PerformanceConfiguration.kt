package com.bugsnag.android.performance

import android.content.Context

class PerformanceConfiguration(val context: Context) {

    var apiKey: String? = null

    var endpoint: String = "https://localhost:8888/performance"

}
