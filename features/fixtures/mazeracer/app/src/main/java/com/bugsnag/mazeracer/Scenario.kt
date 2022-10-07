package com.bugsnag.mazeracer

import com.bugsnag.android.performance.PerformanceConfiguration

abstract class Scenario(
    val config: PerformanceConfiguration,
    val scenarioMetadata: String,
) {
    abstract fun startScenario()

    companion object Factory {
        fun load(
            config: PerformanceConfiguration,
            scenarioName: String,
            scenarioMetadata: String
        ): Scenario {
            try {
                val scenarioClass = Class.forName("com.bugsnag.mazeracer.scenarios.$scenarioName")
                val constructor = scenarioClass.getConstructor(
                    PerformanceConfiguration::class.java,
                    String::class.java
                )

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
