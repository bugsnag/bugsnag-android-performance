package com.bugsnag.android.performance

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.bugsnag.android.performance.internal.plugins.PluginManager
import com.bugsnag.android.performance.internal.processing.ImmutableConfig
import com.bugsnag.android.performance.test.TestTimeoutExecutor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfigurationEndpointTest {
    private val appContext: Application = ApplicationProvider.getApplicationContext()

    private val pluginManager = PluginManager(emptyList(), TestTimeoutExecutor())

    private val hubApiKey = "00000abcdefabcdefabcdefabcdefabcd"

    private val regularApiKey = "abcdefabcdefabcdefabcdefabcdefabcd"

    private val customEndpoint = "https://example.com/custom/endpoint"

    @Test
    fun defaultEndpoint_keyStartsWithHubPrefix_usesBugsnagFormat() {
        val perfConfig = PerformanceConfiguration(appContext, hubApiKey)
        val immutable = ImmutableConfig(perfConfig, pluginManager)
        val expected = "https://$hubApiKey.otlp.bugsnag.smartbear.com/v1/traces"
        assertEquals(expected, immutable.endpoint)
    }

    @Test
    fun defaultEndpoint_keyDoesNotStartWithHubPrefix_usesBugsnagFormat() {
        val perfConfig = PerformanceConfiguration(appContext, regularApiKey)
        val immutable = ImmutableConfig(perfConfig, pluginManager)
        val expected = "https://$regularApiKey.otlp.bugsnag.com/v1/traces"
        assertEquals(expected, immutable.endpoint)
    }

    @Test
    fun customEndpoint_overrideInConfiguration_isRespected() {
        val perfConfig =
            PerformanceConfiguration(appContext, regularApiKey)
                .apply {
                    endpoint = customEndpoint
                }

        val immutable = ImmutableConfig(perfConfig, pluginManager)
        assertEquals(customEndpoint, immutable.endpoint)
    }

    @Test
    fun defaultEndpoint_hubPrefixButEndpointOverridden_usesCustomEndpoint() {
        val perfConfig =
            PerformanceConfiguration(appContext, hubApiKey)
                .apply {
                    endpoint = customEndpoint
                }

        val immutable = ImmutableConfig(perfConfig, pluginManager)
        assertEquals(customEndpoint, immutable.endpoint)
    }
}
