package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
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
            "cpu_disabled", "memory_disabled", "all_enabled", "sub_metrics" ->
                testManualSession(scenarioMetadata)
            "concurrent" -> testConcurrent()
            "force_terminate" -> testOpenSession("Will Be Lost")
            "switch_off" -> testOpenSession("Switch Off")
            "high_cpu" -> testHighCpu()
            "high_memory" -> testHighMemory()
            "parenting" -> testParenting()
        }
    }

    private fun testAggregates() {
        launch {
            BugsnagPerformance.startAppSessionSpan("Aggregates")
            delay(DELAY_AGGREGATES) // Ensure multiple samples
            BugsnagPerformance.endAppSessionSpan()
        }
    }

    private fun testHighCpu() {
        launch {
            BugsnagPerformance.startAppSessionSpan("High CPU")
            val end = System.currentTimeMillis() + DURATION_HIGH_CPU
            while (System.currentTimeMillis() < end) {
                // Burn CPU
                for (i in 0..CPU_BURN_ITERATIONS) {
                    Math.sqrt(i.toDouble())
                }
            }
            BugsnagPerformance.endAppSessionSpan()
        }
    }

    private fun testHighMemory() {
        launch {
            BugsnagPerformance.startAppSessionSpan("High Memory")
            delay(DELAY_MEMORY_INITIAL)
            val list = mutableListOf<ByteArray>()
            for (i in 0..MEMORY_ALLOCATION_ITERATIONS) {
                list.add(ByteArray(BYTES_PER_MB)) // 1MB each
                delay(DELAY_MEMORY_STEP)
            }
            BugsnagPerformance.endAppSessionSpan()
        }
    }

    private fun testParenting() {
        launch {
            BugsnagPerformance.startAppSessionSpan("Parenting Test")
            delay(DELAY_PARENTING)
            BugsnagPerformance.startSpan("ChildSpan").end()
            BugsnagPerformance.endAppSessionSpan()
        }
    }

    private fun testSingleSample() {
        launch {
            BugsnagPerformance.startAppSessionSpan("Single Sample")
            delay(DELAY_SINGLE_SAMPLE) // End before second sample
            BugsnagPerformance.endAppSessionSpan()
        }
    }

    private fun testManualSession(spanName: String) {
        launch {
            BugsnagPerformance.startAppSessionSpan(spanName)
            delay(DELAY_MANUAL_SESSION)
            BugsnagPerformance.endAppSessionSpan()
        }
    }

    private fun testConcurrent() {
        launch {
            // This will start first session
            BugsnagPerformance.startAppSessionSpan("Session 1")
            delay(DELAY_CONCURRENT)
            // This should close Session 1 and start Session 2
            BugsnagPerformance.startAppSessionSpan("Session 2")
            delay(DELAY_CONCURRENT)
            BugsnagPerformance.endAppSessionSpan()
        }
    }

    private fun testOpenSession(spanName: String) {
        launch {
            BugsnagPerformance.startAppSessionSpan(spanName)
            // No end called, app will be killed by Maze Runner
        }
    }

    companion object {
        private const val DELAY_AGGREGATES = 2500L
        private const val DURATION_HIGH_CPU = 2000L
        private const val CPU_BURN_ITERATIONS = 1000
        private const val DELAY_MEMORY_INITIAL = 500L
        private const val MEMORY_ALLOCATION_ITERATIONS = 10
        private const val BYTES_PER_MB = 1024 * 1024
        private const val DELAY_MEMORY_STEP = 100L
        private const val DELAY_PARENTING = 500L
        private const val DELAY_SINGLE_SAMPLE = 200L
        private const val DELAY_MANUAL_SESSION = 1500L
        private const val DELAY_CONCURRENT = 500L
    }
}
