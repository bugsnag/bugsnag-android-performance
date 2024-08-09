package com.bugsnag.android.performance.internal

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanEndCallback
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
            versionCode = 543L
            appVersion = "9.8.1"
            tracePropagationUrls = emptySet<Pattern>()
        }

        val immutableConfig = ImmutableConfig(perfConfig)

        assertEquals(perfConfig.context, immutableConfig.application)
        assertEquals(perfConfig.apiKey, immutableConfig.apiKey)
        assertEquals(perfConfig.endpoint, immutableConfig.endpoint)
        assertEquals(perfConfig.autoInstrumentAppStarts, immutableConfig.autoInstrumentAppStarts)
        assertEquals(TEST_PACKAGE_NAME, immutableConfig.serviceName)
        assertEquals(perfConfig.releaseStage, immutableConfig.releaseStage)
        assertEquals(perfConfig.enabledReleaseStages, immutableConfig.enabledReleaseStages)
        assertEquals(perfConfig.versionCode, immutableConfig.versionCode)
        assertEquals(perfConfig.appVersion, immutableConfig.appVersion)
        assertEquals(perfConfig.tracePropagationUrls, immutableConfig.tracePropagationUrls)
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
    fun enableAllReleaseStages() {
        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY)
        perfConfig.releaseStage = "this is a very interesting releaseStage"
        perfConfig.enabledReleaseStages = null

        val immutableConfig = ImmutableConfig(perfConfig)
        assertTrue(immutableConfig.isReleaseStageEnabled)
    }

    @Test
    fun noopLoggerInProduction() {
        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY)
        perfConfig.releaseStage = RELEASE_STAGE_PRODUCTION

        val immutableConfig = ImmutableConfig(perfConfig)
        assertSame(NoopLogger, immutableConfig.logger)
    }

    @Test
    fun debugLoggerDefault() {
        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY)
        perfConfig.releaseStage = RELEASE_STAGE_DEVELOPMENT

        val immutableConfig = ImmutableConfig(perfConfig)
        assertSame(DebugLogger, immutableConfig.logger)
    }

    @Test
    fun overrideLogger() {
        val testLogger = object : Logger {
            override fun e(msg: String) = Unit
            override fun e(msg: String, throwable: Throwable) = Unit
            override fun w(msg: String) = Unit
            override fun w(msg: String, throwable: Throwable) = Unit
            override fun i(msg: String) = Unit
            override fun i(msg: String, throwable: Throwable) = Unit
            override fun d(msg: String) = Unit
            override fun d(msg: String, throwable: Throwable) = Unit
        }

        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY)
        perfConfig.logger = testLogger

        val immutableConfig = ImmutableConfig(perfConfig)
        assertSame(testLogger, immutableConfig.logger)
    }

    @Test
    fun versionCodeFromContext() {
        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY)
        val immutableConfig = ImmutableConfig(perfConfig)

        assertEquals(TEST_VERSION_CODE.toLong(), immutableConfig.versionCode)
    }

    @Test
    fun appVersionFromContext() {
        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY)
        val immutableConfig = ImmutableConfig(perfConfig)

        assertEquals(TEST_VERSION_NAME, immutableConfig.appVersion)
    }

    @Test
    fun invalidApiKeyLogged() {
        val logger = mock<Logger>()
        Logger.delegate = logger

        val perfConfig = PerformanceConfiguration(mockedContext(), "bad-api-key")
        ImmutableConfig(perfConfig)

        verify(logger).w(startsWith("Invalid configuration"))
    }

    @Test
    fun upperCaseApiKeyLogged() {
        val logger = mock<Logger>()
        Logger.delegate = logger

        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY.uppercase())
        ImmutableConfig(perfConfig)

        verify(logger).w(startsWith("Invalid configuration"))
    }

    @Test
    fun shortApiKeyLogged() {
        val logger = mock<Logger>()
        Logger.delegate = logger

        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY.substring(0, 30))
        ImmutableConfig(perfConfig)

        verify(logger).w(startsWith("Invalid configuration"))
    }

    @Test
    fun longApiKeyLogged() {
        val logger = mock<Logger>()
        Logger.delegate = logger

        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY + "bad")
        ImmutableConfig(perfConfig)

        verify(logger).w(startsWith("Invalid configuration"))
    }

    @Test
    fun EditSpandEndCallbacks() {
        val perfConfig = PerformanceConfiguration(mockedContext(), TEST_API_KEY).apply {
            endpoint = "https://test.com/testing"
            releaseStage = "staging"
            enabledReleaseStages = setOf("staging", "production")
            autoInstrumentAppStarts = false
            autoInstrumentActivities = AutoInstrument.START_ONLY
            versionCode = 543L
            appVersion = "9.8.1"
            tracePropagationUrls = emptySet<Pattern>()
            spanEndCallbacks = mutableListOf<SpanEndCallback>()
        }

        val spanEndCallback1 = SpanEndCallback { span: Span ->
            span as SpanImpl
            span.setAttribute("Test Number", 1111)
            true
        }
        val spanEndCallback2 = SpanEndCallback { span: Span ->
            span as SpanImpl
            span.setAttribute("Test Number", 2222)
            true
        }
        val spanEndCallback3 = SpanEndCallback { span: Span ->
            span as SpanImpl
            span.setAttribute("Test Number", 3333)
            true
        }

        perfConfig.addOnSpanEndCallback(spanEndCallback1)
        perfConfig.addOnSpanEndCallback(spanEndCallback2)
        perfConfig.removeOnSpanEndCallback(spanEndCallback2)
        perfConfig.addOnSpanEndCallback(spanEndCallback3)

        assertEquals(perfConfig.spanEndCallbacks.size, 2)
        assertEquals(perfConfig.spanEndCallbacks.first(), spanEndCallback1)
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
