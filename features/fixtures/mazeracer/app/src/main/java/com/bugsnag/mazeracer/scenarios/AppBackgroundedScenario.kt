package com.bugsnag.mazeracer.scenarios

import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.android.performance.measureSpan
import com.bugsnag.mazeracer.Scenario

class AppBackgroundedScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String
) : Scenario(config, scenarioMetadata) {

    private val handler = Handler(Looper.getMainLooper())

    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 100
        // this should be longer than the Mazerunner timeout
        InternalDebug.workerSleepMs = 90_000L
    }

    override fun startScenario() {
        BugsnagPerformance.start(config)

        measureSpan("Span 1") {
            Thread.sleep(1)
        }

        // we send ourselves to the background, since the Appium action isn't very reliable
        handler.post {
            context.startActivity(
                Intent().apply {
                    setAction(Intent.ACTION_MAIN)
                    addCategory(Intent.CATEGORY_HOME)
                },
            )
        }
    }
}
