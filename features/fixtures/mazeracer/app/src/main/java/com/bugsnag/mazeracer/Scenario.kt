package com.bugsnag.mazeracer

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.bugsnag.android.performance.PerformanceConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

abstract class Scenario(
    val config: PerformanceConfiguration,
    val scenarioMetadata: String,
) : CoroutineScope by MainScope() {
    protected val mainHandler = Handler(Looper.getMainLooper())
    protected val application get() = context.applicationContext as Application

    lateinit var context: Activity

    abstract fun startScenario()

    /**
     * Start the Activity specified by the given Intent, and then `finish()` the entire app
     * once the given Activity closes (ie: wait until `intent` is "finished" before closing the
     * MainActivity).
     *
     * This behaviour is useful in tests that need to flush auto-instrumented spans after the
     * test is complete. The function causes the entire test fixture to be backgrounded which
     * naturally triggers a batch flush (after any pending auto-instrumentation is completed).
     */
    fun startActivityAndFinish(intent: Intent) {
        context.startActivityForResult(intent, MainActivity.REQUEST_CODE_FINISH_ON_RETURN)
    }

    fun runAndFlush(scenario: () -> Unit) {
        scenario()

        mainHandler.post {
            PerformanceTestUtils.flushBatch()
        }
    }

    companion object Factory {
        fun load(
            config: PerformanceConfiguration,
            scenarioName: String,
            scenarioMetadata: String,
        ): Scenario {
            try {
                log("Class.forName(\"com.bugsnag.mazeracer.scenarios.$scenarioName\")")
                val scenarioClass = Class.forName("com.bugsnag.mazeracer.scenarios.$scenarioName")
                log("$scenarioName.getConstructor")
                val constructor =
                    scenarioClass.getConstructor(
                        PerformanceConfiguration::class.java,
                        String::class.java,
                    )

                log("$scenarioName.newInstance($config, $scenarioMetadata)")
                return constructor.newInstance(config, scenarioMetadata) as Scenario
            } catch (cnfe: ClassNotFoundException) {
                throw IllegalArgumentException("Cannot find scenario class $scenarioName", cnfe)
            } catch (nsme: NoSuchMethodException) {
                throw IllegalArgumentException(
                    "$scenarioName does not have a constructor(PerformanceConfiguration, String)",
                    nsme,
                )
            }
        }
    }
}
