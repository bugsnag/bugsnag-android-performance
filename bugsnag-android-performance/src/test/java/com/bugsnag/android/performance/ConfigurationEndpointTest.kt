package com.bugsnag.android.performance.internal.processing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.bugsnag.android.performance.PerformanceConfiguration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImmutableConfigTest {

    private val appContext: Application = ApplicationProvider.getApplicationContext()

    private val hubApiKey = "00000abcdefabcdefabcdefabcdefabcd"

    private val regularApiKey = "abcdefabcdefabcdefabcdefabcdefabcd"

    private val customEndpoint = "https://example.com/custom/endpoint"

    @Test
    fun defaultEndpoint_keyStartsWithHubPrefix_usesInsightHubFormat() {
        val perfConfig = PerformanceConfiguration(appContext, hubApiKey)
        val immutable = ImmutableConfig(perfConfig)
        val expected = "https://$hubApiKey.otlp.insighthub.smartbear.com/v1/traces"
        assertEquals(expected, immutable.endpoint)
    }

    @Test
    fun defaultEndpoint_keyDoesNotStartWithHubPrefix_usesBugsnagFormat() {
        val perfConfig = PerformanceConfiguration(appContext, regularApiKey)
        val immutable = ImmutableConfig(perfConfig)
        val expected = "https://$regularApiKey.otlp.bugsnag.com/v1/traces"
        assertEquals(expected, immutable.endpoint)
    }

    @Test
    fun customEndpoint_overrideInConfiguration_isRespected() {
        val perfConfig = PerformanceConfiguration(appContext, regularApiKey).apply {
            endpoint = customEndpoint
        }
        val immutable = ImmutableConfig(perfConfig)
        assertEquals(customEndpoint, immutable.endpoint)
    }

    @Test
    fun defaultEndpoint_hubPrefixButEndpointOverridden_usesCustomEndpoint() {
        val perfConfig = PerformanceConfiguration(appContext, hubApiKey).apply {
            endpoint = customEndpoint
        }
        val immutable = ImmutableConfig(perfConfig)
        assertEquals(customEndpoint, immutable.endpoint)
    }
}
