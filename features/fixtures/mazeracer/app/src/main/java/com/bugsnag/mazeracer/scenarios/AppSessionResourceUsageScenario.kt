package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.android.performance.internal.BugsnagPerformanceImpl
import com.bugsnag.mazeracer.log
import com.bugsnag.mazeracer.Scenario
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppSessionResourceUsageScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {

    private val scenarioConfig = mutableMapOf<String, String>()

    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 1
        config.appSessionConfig.autoStartSession = false
        config.autoInstrumentAppStarts = false
    }

    fun configureBugsnag(key: String, value: String) {
        when (key) {
            "cpuMetrics" -> config.enabledMetrics.cpu = value.toBoolean()
            "memoryMetrics" -> config.enabledMetrics.memory = value.toBoolean()
        }
    }

    fun configureScenario(key: String, value: String) {
        scenarioConfig[key] = value
    }

    fun startBugsnag() {
        BugsnagPerformance.start(config)
    }

    override fun startScenario() {
        val type = scenarioConfig["session_type"] ?: ""
        val duration = (scenarioConfig["span_duration"]?.toDouble() ?: 1.0) * 1000
        val workDuration = (scenarioConfig["work_duration"]?.toDouble() ?: 0.0) * 1000
        val abort = scenarioConfig["abort_span"]?.toBoolean() ?: false
        val child = scenarioConfig["create_child_span"]?.toBoolean() ?: false
        val concurrent = scenarioConfig["concurrent_session_type"]

        if (type == "TestManualSpan") {
            launch {
                delay(1000)
                val span = BugsnagPerformance.startSpan("TestManualSpan")
                span.end()
            }
            return
        }

        // Start session span synchronously to ensure it's captured immediately
        try {
            BugsnagPerformance.startAppSessionSpan(type)
        } catch (e: Exception) {
            log("AppSessionResourceUsageScenario: Failed to start app session span", e)
        }

        launch {
            if (workDuration > 0) {
                val end = System.currentTimeMillis() + workDuration.toLong()
                while (System.currentTimeMillis() < end) {
                    for (i in 0..100) {
                        Math.sqrt(i.toDouble())
                    }
                }
            }

            if (child) {
                BugsnagPerformance.startSpan("ChildSpanInsideSession").end()
            }

            if (concurrent != null) {
                // End session A first
                BugsnagPerformance.endAppSessionSpan()
                delay(500)
                // Start and run session B
                BugsnagPerformance.startAppSessionSpan(concurrent)
                delay(duration.toLong())
                BugsnagPerformance.endAppSessionSpan()
            } else {
                delay(duration.toLong())
                if (!abort) {
                    BugsnagPerformance.endAppSessionSpan()
                }
            }
        }
    }
}
