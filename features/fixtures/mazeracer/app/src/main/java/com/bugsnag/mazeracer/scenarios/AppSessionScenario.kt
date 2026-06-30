package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.BugsnagPerformanceImpl
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.Scenario
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppSessionScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {

    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 1
        config.appSessionConfig.autoStartSession = false
        // Enable metrics by default unless specifically disabled
        config.enabledMetrics.cpu = true
        config.enabledMetrics.memory = true

        when (scenarioMetadata) {
            "cpu_disabled" -> {
                config.enabledMetrics.cpu = false
            }
            "memory_disabled" -> {
                config.enabledMetrics.memory = false
            }
        }
        BugsnagPerformance.start(config)
    }

    override fun startScenario() {
        when (scenarioMetadata) {
            "aggregates" -> testAggregates()
            "single_sample" -> testSingleSample()
            "cpu_disabled" -> testManualSession()
            "memory_disabled" -> testManualSession()
            "sub_metrics" -> testSubMetrics()
            "concurrent" -> testConcurrent()
            "force_terminate" -> testForceTerminate()
            "switch_off" -> testSwitchOff()
            "all_enabled" -> testManualSession()
            "high_cpu" -> testHighCpu()
            "high_memory" -> testHighMemory()
            "parenting" -> testParenting()
        }
    }

    private fun testAggregates() {
        launch {
            BugsnagPerformance.startAppSessionSpan("Aggregates")
            delay(2500) // Ensure multiple samples
            BugsnagPerformance.endAppSessionSpan()
        }
    }

    private fun testHighCpu() {
        launch {
            BugsnagPerformance.startAppSessionSpan("High CPU")
            val end = System.currentTimeMillis() + 2000
            while (System.currentTimeMillis() < end) {
                // Burn CPU
                for (i in 0..1000) { Math.sqrt(i.toDouble()) }
            }
            BugsnagPerformance.endAppSessionSpan()
        }
    }

    private fun testHighMemory() {
        launch {
            BugsnagPerformance.startAppSessionSpan("High Memory")
            delay(500)
            val list = mutableListOf<ByteArray>()
            for (i in 0..10) {
                list.add(ByteArray(1024 * 1024)) // 1MB each
                delay(100)
            }
            BugsnagPerformance.endAppSessionSpan()
        }
    }

    private fun testParenting() {
        launch {
            BugsnagPerformance.startAppSessionSpan("Parenting Test")
            delay(500)
            BugsnagPerformance.startSpan("ChildSpan").end()
            BugsnagPerformance.endAppSessionSpan()
        }
    }

    private fun testSingleSample() {
        launch {
            BugsnagPerformance.startAppSessionSpan("Single Sample")
            delay(200) // End before second sample
            BugsnagPerformance.endAppSessionSpan()
        }
    }

    private fun testManualSession() {
        launch {
            BugsnagPerformance.startAppSessionSpan("Manual Session")
            delay(1500)
            BugsnagPerformance.endAppSessionSpan()
        }
    }

    private fun testSubMetrics() {
        launch {
            BugsnagPerformance.startAppSessionSpan("Sub Metrics")
            delay(1500)
            BugsnagPerformance.endAppSessionSpan()
        }
    }

    private fun testConcurrent() {
        launch {
            // This will start first session
            BugsnagPerformance.startAppSessionSpan("Session 1")
            delay(500)
            // This should close Session 1 and start Session 2
            BugsnagPerformance.startAppSessionSpan("Session 2")
            delay(500)
            BugsnagPerformance.endAppSessionSpan()
        }
    }

    private fun testForceTerminate() {
        launch {
            BugsnagPerformance.startAppSessionSpan("Will Be Lost")
            // No end called, app will be killed by Maze Runner
        }
    }

    private fun testSwitchOff() {
        launch {
            BugsnagPerformance.startAppSessionSpan("Switch Off")
            // App will be killed by Maze Runner
        }
    }
}
