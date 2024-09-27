package com.bugsnag.android.performance

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfigurationLoaderTest {
    private val apiKey = "cafedecafcafedecafcafedecafcafedecaf"
    private val endpoint = "https://localhost:9876/fake-endpoint"
    private val releaseStage = "staging"
    private val enabledReturnStage = "staging,development,production"
    private val versionCode = 321

    private val bugsnagApiKey = "decafc0ffeedecafc0ffeedecafc0ffeeace"
    private val bugsnagReleaseStage = "production"
    private val bugsnagEnabledReleaseStages = "production,bugsnag"
    private val bugnsagVersionCode = 876

    @Test
    fun loadFromManifestBundle_PerformanceNS() {
        val metadataBundle = Bundle()
        metadataBundle.populatePerformanceNS()

        val config = PerformanceConfiguration.loadFromMetaData(mock(), metadataBundle, null)

        assertPerformanceNSConfig(config)
    }

    @Test
    fun loadFromManifestBundle_BugsnagNS() {
        val metadataBundle = Bundle()
        metadataBundle.populateBugsnagNS()

        val config = PerformanceConfiguration.loadFromMetaData(mock(), metadataBundle, null)

        assertEquals(bugsnagApiKey, config.apiKey)
        assertEquals("https://otlp.bugsnag.com/v1/traces", config.endpoint)
        assertTrue("expected autoInstrumentAppStarts = true", config.autoInstrumentAppStarts)
        assertEquals(AutoInstrument.FULL, config.autoInstrumentActivities)
        assertEquals(bugsnagReleaseStage, config.releaseStage)
        assertEquals(bugnsagVersionCode.toLong(), config.versionCode)
        assertEquals(setOf("bugsnag", "production"), config.enabledReleaseStages)
    }

    @Test
    fun loadFromManifestBundle_OverrideNS() {
        val metadataBundle = Bundle()
        metadataBundle.populateBugsnagNS()
        metadataBundle.populatePerformanceNS()

        val config = PerformanceConfiguration.loadFromMetaData(mock(), metadataBundle, null)
        assertPerformanceNSConfig(config)
    }

    @Test
    fun loadFromBugsnagConfig() {
        val versionCode = 321

        val metadataBundle = Bundle().apply {
            putInt("com.bugsnag.android.VERSION_CODE", versionCode)
        }

        val config = PerformanceConfiguration.loadFromMetaData(mock(), metadataBundle, null)
        assertEquals(versionCode.toLong(), config.versionCode)
    }

    private fun assertPerformanceNSConfig(config: PerformanceConfiguration) {
        assertEquals(apiKey, config.apiKey)
        assertEquals(endpoint, config.endpoint)
        assertFalse("expected autoInstrumentAppStarts = false", config.autoInstrumentAppStarts)
        assertEquals(AutoInstrument.START_ONLY, config.autoInstrumentActivities)
        assertEquals(releaseStage, config.releaseStage)
        assertEquals(versionCode.toLong(), config.versionCode)
        assertEquals(setOf("staging", "development", "production"), config.enabledReleaseStages)
        assertEquals(
            listOf(
                ".*\\.example\\.com",
                "example\\.com",
            ),
            config.tracePropagationUrls.map { it.pattern() },
        )
        assertEquals("my.service.name", config.serviceName)
        assertEquals(123, config.attributeStringValueLimit)
        assertEquals(234, config.attributeArrayLengthLimit)
        assertEquals(345, config.attributeCountLimit)
    }

    private fun Bundle.populatePerformanceNS() {
        putString("com.bugsnag.performance.android.API_KEY", apiKey)
        putString("com.bugsnag.performance.android.ENDPOINT", endpoint)
        putString("com.bugsnag.performance.android.AUTO_INSTRUMENT_ACTIVITIES", "START_ONLY")
        putInt("com.bugsnag.performance.android.VERSION_CODE", versionCode)

        putString("com.bugsnag.performance.android.RELEASE_STAGE", releaseStage)
        putString("com.bugsnag.performance.android.ENABLED_RELEASE_STAGES", enabledReturnStage)

        putBoolean("com.bugsnag.performance.android.AUTO_INSTRUMENT_APP_STARTS", false)

        putString(
            "com.bugsnag.performance.android.TRACE_PROPAGATION_URLS",
            ".*\\.example\\.com,example\\.com",
        )
        putString("com.bugsnag.performance.android.SERVICE_NAME", "my.service.name")

        putInt("com.bugsnag.performance.android.ATTRIBUTE_STRING_VALUE_LIMIT", 123)
        putInt("com.bugsnag.performance.android.ATTRIBUTE_ARRAY_LENGTH_LIMIT", 234)
        putInt("com.bugsnag.performance.android.ATTRIBUTE_COUNT_LIMIT", 345)
    }

    private fun Bundle.populateBugsnagNS() {
        putString("com.bugsnag.android.API_KEY", bugsnagApiKey)
        putString("com.bugsnag.android.AUTO_INSTRUMENT_ACTIVITIES", "OFF")
        putInt("com.bugsnag.android.VERSION_CODE", bugnsagVersionCode)

        putString("com.bugsnag.android.RELEASE_STAGE", bugsnagReleaseStage)
        putString("com.bugsnag.android.ENABLED_RELEASE_STAGES", bugsnagEnabledReleaseStages)

        putBoolean("com.bugsnag.android.AUTO_INSTRUMENT_APP_STARTS", true)
    }
}
