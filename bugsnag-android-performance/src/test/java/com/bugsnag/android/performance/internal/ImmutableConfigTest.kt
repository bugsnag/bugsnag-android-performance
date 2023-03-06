package com.bugsnag.android.performance.internal

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.PerformanceConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

class ImmutableConfigTest {
    @Test
    fun copyFromPerformanceConfiguration() {
        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY).apply {
            endpoint = "https://test.com/testing"
            releaseStage = "staging"
            enabledReleaseStages = setOf("staging", "production")
            autoInstrumentAppStarts = false
            autoInstrumentActivities = AutoInstrument.START_ONLY
            versionCode = 543L
            samplingProbability = 0.25
        }

        val immutableConfig = ImmutableConfig(perfConfig)

        assertEquals(perfConfig.context, immutableConfig.application)
        assertEquals(perfConfig.apiKey, immutableConfig.apiKey)
        assertEquals(perfConfig.endpoint, immutableConfig.endpoint)
        assertEquals(perfConfig.autoInstrumentAppStarts, immutableConfig.autoInstrumentAppStarts)
        assertEquals(TEST_PACKAGE_NAME, immutableConfig.packageName)
        assertEquals(perfConfig.releaseStage, immutableConfig.releaseStage)
        assertEquals(perfConfig.enabledReleaseStages, immutableConfig.enabledReleaseStages)
        assertEquals(perfConfig.versionCode, immutableConfig.versionCode)
        assertEquals(perfConfig.samplingProbability, immutableConfig.samplingProbability, 0.001)
    }

    @Test
    fun immutableEnabledReleaseStages() {
        val releaseStages = mutableSetOf("staging")
        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY)
        perfConfig.enabledReleaseStages = releaseStages

        val immutableConfig = ImmutableConfig(perfConfig)
        assertNotSame(releaseStages, immutableConfig.enabledReleaseStages)
        assertEquals(releaseStages, immutableConfig.enabledReleaseStages)

        releaseStages.add("production")
        assertNotEquals(releaseStages, immutableConfig.enabledReleaseStages)
    }

    @Test
    fun versionCodeFromContext() {
        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY)
        val immutableConfig = ImmutableConfig(perfConfig)

        assertEquals(TEST_VERSION_CODE.toLong(), immutableConfig.versionCode)
    }

    private fun mockedContext(): Context {
        val packageInfo = mock<PackageInfo> { info ->
            // versionCode is a Java field, not a getter
            @Suppress("DEPRECATION")
            info.versionCode = TEST_VERSION_CODE

            on { info.longVersionCode } doReturn TEST_VERSION_CODE.toLong()
        }

        val packageManager = mock<PackageManager> { pm ->
            on { (pm.getPackageInfo(eq(TEST_PACKAGE_NAME), any())) } doReturn packageInfo
        }

        return mock<Application> { ctx ->
            on { ctx.packageName } doReturn TEST_PACKAGE_NAME
            on { ctx.applicationContext } doReturn ctx
            on { ctx.packageManager } doReturn packageManager
        }
    }

    companion object {
        const val TEST_PACKAGE_NAME = "com.test.pckname"
        const val TEST_API_KEY = "decafbaddecafbaddecafbaddecafbad"
        const val TEST_VERSION_CODE = 987654321
    }
}
