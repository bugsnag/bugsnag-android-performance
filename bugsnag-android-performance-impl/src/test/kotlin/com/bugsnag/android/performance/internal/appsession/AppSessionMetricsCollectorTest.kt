package com.bugsnag.android.performance.internal.appsession

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bugsnag.android.performance.EnabledMetrics
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class AppSessionMetricsCollectorTest {

    @Test
    fun testPssSamplingCarryOverLogic() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabledMetrics = EnabledMetrics(memory = true)
        // PSS every 30s
        val collector = AppSessionMetricsCollector(
            context,
            enabledMetrics,
            samplingIntervalMs = 1000L,
            deviceMemorySamplingIntervalMs = 30000L,
        ).apply {
            pssSupplier = { 1024L } // Return constant non-zero value for tests
        }

        // Sample 0: triggers PSS because lastPssSampleUptime is 0L
        collector.takeSample()

        // Take 29 more samples at 1s intervals. 
        // All should CARRY OVER the value from sample 0.
        repeat(29) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(1000L))
            collector.takeSample()
        }

        val metrics = collector.stop()
        // stop() calls takeSample() one last time. Total: 31.

        assertEquals(31, metrics.runtimeMemoryCount)
        assertEquals(31, metrics.deviceMemoryCount) // Verify carry-over fills the array
    }

    @Test
    fun testValidationDefaultsIfOutsideRange() {
        val config = com.bugsnag.android.performance.AppSessionConfig()

        // Too small (< 1s) -> defaults to 1s
        config.samplingIntervalMs = 500L
        assertEquals(1000L, config.samplingIntervalMs)

        // Too large (> 60s) -> defaults to 1s
        config.samplingIntervalMs = 70000L
        assertEquals(1000L, config.samplingIntervalMs)

        // Valid (in range [1s, 60s]) -> stays same
        config.samplingIntervalMs = 45000L
        assertEquals(45000L, config.samplingIntervalMs)
    }
}
