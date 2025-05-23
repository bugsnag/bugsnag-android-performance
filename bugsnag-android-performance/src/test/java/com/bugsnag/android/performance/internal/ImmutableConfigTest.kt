package com.bugsnag.android.performance.internal

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.EnabledMetrics
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.OnSpanEndCallback
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.plugins.PluginManager
import com.bugsnag.android.performance.internal.processing.ImmutableConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.regex.Pattern

class ImmutableConfigTest {

    val enabledMetricsSample: EnabledMetrics = EnabledMetrics(true)

    @Before
    fun setupLogger() {
        Logger.delegate = NoopLogger
    }

    @Test
    fun copyFromPerformanceConfiguration() {
        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY).apply {
            endpoint = "https://test.com/testing"
            releaseStage = "staging"
            enabledReleaseStages = setOf("staging", "production")
            autoInstrumentAppStarts = false
            autoInstrumentActivities = AutoInstrument.START_ONLY
            enabledMetrics = enabledMetricsSample
            enabledMetrics.rendering = false
            versionCode = 543L
            appVersion = "9.8.1"
            tracePropagationUrls = emptySet<Pattern>()
        }

        val immutableConfig = ImmutableConfig(perfConfig, PluginManager(emptyList()))

        assertEquals(perfConfig.context, immutableConfig.application)
        assertEquals(perfConfig.apiKey, immutableConfig.apiKey)
        assertEquals(perfConfig.endpoint, immutableConfig.endpoint)
        assertEquals(perfConfig.autoInstrumentAppStarts, immutableConfig.autoInstrumentAppStarts)
        assertEquals(perfConfig.enabledMetrics.rendering, immutableConfig.enabledMetrics.rendering)
        assertEquals(TEST_PACKAGE_NAME, immutableConfig.serviceName)
        assertEquals(perfConfig.releaseStage, immutableConfig.releaseStage)
        assertEquals(perfConfig.enabledReleaseStages, immutableConfig.enabledReleaseStages)
        assertEquals(perfConfig.versionCode, immutableConfig.versionCode)
        assertEquals(perfConfig.appVersion, immutableConfig.appVersion)
        assertEquals(perfConfig.tracePropagationUrls, immutableConfig.tracePropagationUrls)
        assertEquals(perfConfig.enabledMetrics, immutableConfig.enabledMetrics)
    }

    @Test
    fun defaultEndpointHasApiKey() {
        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY)
        val immutableConfig = ImmutableConfig(perfConfig, PluginManager(emptyList()))

        assertEquals(
            "https://$TEST_API_KEY.otlp.bugsnag.com/v1/traces",
            immutableConfig.endpoint,
        )
    }

    @Test
    fun immutableEnabledReleaseStages() {
        val releaseStages = mutableSetOf("staging")
        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY)
        perfConfig.enabledReleaseStages = releaseStages

        val immutableConfig = ImmutableConfig(perfConfig, PluginManager(emptyList()))
        assertNotSame(releaseStages, immutableConfig.enabledReleaseStages)
        assertEquals(releaseStages, immutableConfig.enabledReleaseStages)

        releaseStages.add("production")
        assertNotEquals(releaseStages, immutableConfig.enabledReleaseStages)
    }

    @Test
    fun enableAllReleaseStages() {
        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY)
        perfConfig.releaseStage = "this is a very interesting releaseStage"
        perfConfig.enabledReleaseStages = null

        val immutableConfig = ImmutableConfig(perfConfig, PluginManager(emptyList()))
        assertTrue(immutableConfig.isReleaseStageEnabled)
    }

    @Test
    fun versionCodeFromContext() {
        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY)
        val immutableConfig = ImmutableConfig(perfConfig, PluginManager(emptyList()))

        assertEquals(TEST_VERSION_CODE.toLong(), immutableConfig.versionCode)
    }

    @Test
    fun appVersionFromContext() {
        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY)
        val immutableConfig = ImmutableConfig(perfConfig, PluginManager(emptyList()))

        assertEquals(TEST_VERSION_NAME, immutableConfig.appVersion)
    }

    @Test
    fun invalidApiKeyLogged() {
        val logger = mock<Logger>()
        Logger.delegate = logger

        val perfConfig = PerformanceConfiguration(mockedContext(), "bad-api-key")
        ImmutableConfig(perfConfig, PluginManager(emptyList()))

        verify(logger).w(startsWith("Invalid configuration"))
    }

    @Test
    fun upperCaseApiKeyLogged() {
        val logger = mock<Logger>()
        Logger.delegate = logger

        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY.uppercase())
        ImmutableConfig(perfConfig, PluginManager(emptyList()))

        verify(logger).w(startsWith("Invalid configuration"))
    }

    @Test
    fun shortApiKeyLogged() {
        val logger = mock<Logger>()
        Logger.delegate = logger

        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY.substring(0, 30))
        ImmutableConfig(perfConfig, PluginManager(emptyList()))

        verify(logger).w(startsWith("Invalid configuration"))
    }

    @Test
    fun longApiKeyLogged() {
        val logger = mock<Logger>()
        Logger.delegate = logger

        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY + "bad")
        ImmutableConfig(perfConfig, PluginManager(emptyList()))

        verify(logger).w(startsWith("Invalid configuration"))
    }

    @Test
    fun addRemoveSpanEndCallbacks() {
        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY).apply {
            endpoint = "https://test.com/testing"
            releaseStage = "staging"
            enabledReleaseStages = setOf("staging", "production")
            autoInstrumentAppStarts = false
            autoInstrumentActivities = AutoInstrument.START_ONLY
            versionCode = 543L
            appVersion = "9.8.1"
            tracePropagationUrls = emptySet<Pattern>()
            enabledMetrics = enabledMetricsSample
        }

        val spanEndCallback1 = DummyCallback()
        val spanEndCallback2 = DummyCallback()
        val spanEndCallback3 = DummyCallback()

        perfConfig.addOnSpanEndCallback(spanEndCallback1)
        perfConfig.addOnSpanEndCallback(spanEndCallback2)
        perfConfig.addOnSpanEndCallback(spanEndCallback3)
        perfConfig.removeOnSpanEndCallback(spanEndCallback2)

        assertEquals(2, perfConfig.spanEndCallbacks.size)
        assertSame(spanEndCallback1, perfConfig.spanEndCallbacks[0])
        assertSame(spanEndCallback3, perfConfig.spanEndCallbacks[1])
    }

    private class DummyCallback : OnSpanEndCallback {
        override fun onSpanEnd(span: Span): Boolean {
            return true
        }
    }

    private fun mockedContext(): Context {
        val packageInfo = mock<PackageInfo> { info ->
            // versionCode is a Java field, not a getter
            @Suppress("DEPRECATION")
            info.versionCode = TEST_VERSION_CODE
            info.versionName = TEST_VERSION_NAME

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
        const val TEST_VERSION_NAME = "7.6.5"
    }
}
