package com.bugsnag.mazeracer.scenarios

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.measureSpan
import com.bugsnag.mazeracer.Scenario
import java.util.concurrent.TimeUnit

class BackgroundSpanScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        BugsnagPerformance.start(config)

        // we wait until the app is in the background before starting this span
        application.registerActivityLifecycleCallbacks(
            object :
                Application.ActivityLifecycleCallbacks {
                override fun onActivityStopped(activity: Activity) {
                    Log.i("BackgroundSpan", "onActivityStopped($activity)")
                    mainHandler.postDelayed(
                        {
                            measureSpan("BackgroundSpan") {
                                Thread.sleep(1)
                            }
                        },
                        TimeUnit.SECONDS.toMillis(1),
                    )
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) {
                    Log.i("BackgroundSpan", "onActivityCreated($activity)")
                }

                override fun onActivityStarted(activity: Activity) {
                    Log.i("BackgroundSpan", "onActivityStarted($activity)")
                }

                override fun onActivityResumed(activity: Activity) {
                    Log.i("BackgroundSpan", "onActivityResumed($activity)")
                }

                override fun onActivityPaused(activity: Activity) {
                    Log.i("BackgroundSpan", "onActivityPaused($activity)")
                }

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) {
                    Log.i("BackgroundSpan", "onActivitySaveInstanceState($activity)")
                }

                override fun onActivityDestroyed(activity: Activity) {
                    Log.i("BackgroundSpan", "onActivityDestroyed($activity)")
                }
            },
        )

        // we send ourselves to the background, since the Appium action isn't very reliable
        mainHandler.post {
            context.startActivity(
                Intent().apply {
                    setAction(Intent.ACTION_MAIN)
                    addCategory(Intent.CATEGORY_HOME)
                },
            )
        }
    }
}
