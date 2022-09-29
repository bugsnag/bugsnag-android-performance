package com.bugsnag.android.performance

import android.content.Context
import android.net.Uri

class BugsnagPerformanceConfiguration constructor(
    val context: Context,
) {
    var endpoint: Uri = Uri.parse("https://localhost:8888/performance")
}
