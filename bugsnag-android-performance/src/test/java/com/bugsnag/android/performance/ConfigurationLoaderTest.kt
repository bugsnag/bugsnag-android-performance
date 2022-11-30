package com.bugsnag.android.performance

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

class ConfigurationLoaderTest {
    @Test
    fun loadFromManifestBundle() {
        val apiKey = "cafedecafcafedecafcafedecafcafedecaf"
        val endpoint = "https://localhost:9876/fake-endpoint"
        val releaseStage = "staging"
        val versionCode = 321

        val metadataBundle = mock<Bundle> {
            on { getString("com.bugsnag.android.API_KEY") } doReturn apiKey
            on { getString("com.bugsnag.performance.android.ENDPOINT") } doReturn endpoint
            on { getString("com.bugsnag.performance.android.AUTO_INSTRUMENT_ACTIVITIES") } doReturn "START_ONLY"
            on { containsKey("com.bugsnag.performance.android.VERSION_CODE") } doReturn true
            on { getInt("com.bugsnag.performance.android.VERSION_CODE") } doReturn versionCode

            on {
                getString(eq("com.bugsnag.performance.android.RELEASE_STAGE"), anyOrNull())
            } doReturn releaseStage

            on {
                getBoolean(eq("com.bugsnag.performance.android.AUTO_INSTRUMENT_APP_STARTS"), any())
            } doReturn false
        }

        val config = PerformanceConfiguration.loadFromMetaData(mock(), metadataBundle, null)

        assertEquals(apiKey, config.apiKey)
        assertEquals(endpoint, config.endpoint)
        assertFalse("expected autoInstrumentAppStarts = false", config.autoInstrumentAppStarts)
        assertEquals(AutoInstrument.START_ONLY, config.autoInstrumentActivities)
        assertEquals(releaseStage, config.releaseStage)
        assertEquals(versionCode.toLong(), config.versionCode)
    }

    @Test
    fun loadFromBugsnagConfig() {
        val versionCode = 321

        val metadataBundle = mock<Bundle> {
            on { containsKey("com.bugsnag.android.VERSION_CODE") } doReturn true
            on { getInt("com.bugsnag.android.VERSION_CODE") } doReturn versionCode
        }

        val config = PerformanceConfiguration.loadFromMetaData(mock(), metadataBundle, null)
        assertEquals(versionCode.toLong(), config.versionCode)
    }
}
