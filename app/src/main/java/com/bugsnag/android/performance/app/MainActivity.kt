package com.bugsnag.android.performance.app

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.BugsnagPerformanceConfiguration
import com.bugsnag.android.performance.measureSpan

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BugsnagPerformance.start(
            BugsnagPerformanceConfiguration(
                endpoint = Uri.parse("http://10.0.2.2:19283")
            )
        )

        measureSpan("hello") {
            for (i in 0..10_000) {
                measureSpan("spam") {
                    Log.i("LogSpam", "Spammed you ${i + 1} times so far!")
                }
            }
        }
    }
}