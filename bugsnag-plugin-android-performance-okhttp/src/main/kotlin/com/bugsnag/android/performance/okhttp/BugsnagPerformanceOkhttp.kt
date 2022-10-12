package com.bugsnag.android.performance.okhttp

import android.util.Log
import okhttp3.Call
import okhttp3.EventListener
import java.io.IOException

object BugsnagPerformanceOkhttp: EventListener() {

    override fun callStart(call: Call) {
        Log.d("BugsnagPerfOkhttp", "callStart")
    }

    override fun callEnd(call: Call) {
        Log.d("BugsnagPerfOkhttp", "callEnd")
    }

    override fun callFailed(call: Call, ioe: IOException) {
        Log.d("BugsnagPerfOkhttp", "callFailed")
    }

    override fun canceled(call: Call) {
        Log.d("BugsnagPerfOkhttp", "canceled")
    }
}
