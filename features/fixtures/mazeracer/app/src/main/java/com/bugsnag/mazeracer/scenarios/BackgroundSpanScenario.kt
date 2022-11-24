package com.bugsnag.mazeracer.scenarios

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.android.performance.measureSpan
import com.bugsnag.mazeracer.Scenario
import java.util.concurrent.TimeUnit

class BackgroundSpanScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 1
    }

    override fun startScenario() {
        BugsnagPerformance.start(config)

        val application = context.applicationContext as Application

        // we wait until the app is in the background before starting this span
        application.registerActivityLifecycleCallbacks(object :
                Application.ActivityLifecycleCallbacks {
                private val handler = Handler(Looper.getMainLooper())
                override fun onActivityStopped(activity: Activity) {
                    handler.postDelayed(
                        {
                            measureSpan("BackgroundSpan") {
                                Thread.sleep(1)
                            }
                        },
                        TimeUnit.SECONDS.toMillis(1)
                    )
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            })
    }
}
