package com.bugsnag.android.performance

import android.net.Uri

data class BugsnagPerformanceConfiguration @JvmOverloads constructor(
    var endpoint: Uri = Uri.parse("https://localhost:8888/performance"),
)
