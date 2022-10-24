package com.bugsnag.mazeracer

import android.content.Context
import com.bugsnag.android.performance.PerformanceConfiguration

abstract class Scenario(
    val config: PerformanceConfiguration,
    val scenarioMetadata: String,
) {
    lateinit var context: Context

    abstract fun startScenario()

    companion object Factory {
        fun load(
            config: PerformanceConfiguration,
            scenarioName: String,
            scenarioMetadata: String
        ): Scenario {
            try {
                log("Class.forName(\"com.bugsnag.mazeracer.scenarios.$scenarioName\")")
                val scenarioClass = Class.forName("com.bugsnag.mazeracer.scenarios.$scenarioName")
                log("$scenarioName.getConstructor")
                val constructor = scenarioClass.getConstructor(
                    PerformanceConfiguration::class.java,
                    String::class.java
                )

                log("$scenarioName.newInstance($config, $scenarioMetadata)")
                return constructor.newInstance(config, scenarioMetadata) as Scenario
            } catch (cnfe: ClassNotFoundException) {
                throw IllegalArgumentException("Cannot find scenario class $scenarioName", cnfe)
            } catch (nsme: NoSuchMethodException) {
                throw IllegalArgumentException(
                    "$scenarioName does not have a constructor(PerformanceConfiguration, String)",
                    nsme
                )
            }
        }
    }
}
