package com.bugsnag.android.performance.internal

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.bugsnag.android.performance.AutoInstrumentationCache
import com.bugsnag.android.performance.DoNotEndAppStart
import com.bugsnag.android.performance.DoNotInstrument
import com.bugsnag.android.performance.PerformanceConfiguration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

internal class AutoInstrumentationCacheTest {

    @DoNotInstrument
    class DoNotInstrumentActivity : Activity()

    @DoNotEndAppStart
    class DoNotEndAppStartActivity : Activity()

    @DoNotInstrument
    @DoNotEndAppStart
    class NoInstrumentationAppStartActivity : Activity()

    class NoAnnotatedActivity : Activity()

    private val perfConfig =
        PerformanceConfiguration(mockedContext(), TEST_API_KEY)

    @Test
    fun testIsInstrumentationEnabled() {
        val autoInstrumentationCache =
            AutoInstrumentationCache()
        autoInstrumentationCache.configuration(perfConfig.doNotEndAppStart, perfConfig.doNotAutoInstrument)
        val result =
            autoInstrumentationCache.isInstrumentationEnabled(DoNotInstrumentActivity::class.java)
        assertFalse(result)
    }

    @Test
    fun testIsAppStartActivity() {
        val autoInstrumentationCache =
            AutoInstrumentationCache()
        autoInstrumentationCache.configuration(perfConfig.doNotEndAppStart, perfConfig.doNotAutoInstrument)
        val result =
            autoInstrumentationCache.isAppStartActivity(DoNotEndAppStartActivity::class.java)
        assertTrue(result)
    }

    @Test
    fun testInstrumentationIsEnabled() {
        val autoInstrumentationCache =
            AutoInstrumentationCache()
        autoInstrumentationCache.configuration(perfConfig.doNotEndAppStart, perfConfig.doNotAutoInstrument)
        val result =
            autoInstrumentationCache.isInstrumentationEnabled(NoAnnotatedActivity::class.java)
        assertTrue(result)
    }

    @Test
    fun testAppStartActivityIsEnabled() {
        val autoInstrumentationCache =
            AutoInstrumentationCache()
        autoInstrumentationCache.configuration(perfConfig.doNotEndAppStart, perfConfig.doNotAutoInstrument)
        val result =
            autoInstrumentationCache.isAppStartActivity(NoAnnotatedActivity::class.java)
        assertFalse(result)
    }

    @Test
    fun testInstrumentationNotEnabled() {
        val autoInstrumentationCache =
            AutoInstrumentationCache()
        autoInstrumentationCache.configuration(perfConfig.doNotEndAppStart, perfConfig.doNotAutoInstrument)
        val result =
            autoInstrumentationCache.isInstrumentationEnabled(NoInstrumentationAppStartActivity::class.java)
        assertFalse(result)
    }

    @Test
    fun testAppStartActivityNotEnabled() {
        val autoInstrumentationCache =
            AutoInstrumentationCache()
        autoInstrumentationCache.configuration(perfConfig.doNotEndAppStart, perfConfig.doNotAutoInstrument)
        val result =
            autoInstrumentationCache.isAppStartActivity(NoInstrumentationAppStartActivity::class.java)
        assertTrue(result)
    }

    @Test
    fun testDoNotInstrumentActivities() {
        val doNotInstrumentActivities = hashSetOf<Class<Any>>()
        doNotInstrumentActivities.add(DoNotInstrumentActivity::class.java as Class<Any>)
        doNotInstrumentActivities.add(DoNotEndAppStartActivity::class.java as Class<Any>)
        perfConfig.doNotAutoInstrument = doNotInstrumentActivities

        val autoInstrumentationCache =
            AutoInstrumentationCache()
        autoInstrumentationCache.configuration(perfConfig.doNotEndAppStart, perfConfig.doNotAutoInstrument)

        val isInstrumentationResult1 =
            autoInstrumentationCache.isInstrumentationEnabled(perfConfig.doNotAutoInstrument.last())
        assertTrue(isInstrumentationResult1)

        val isInstrumentationResult2 =
            autoInstrumentationCache.isInstrumentationEnabled(perfConfig.doNotAutoInstrument.first())
        assertTrue(isInstrumentationResult2)
    }

    @Test
    fun testDoNotEndAppStartActivities() {
        val doNotEndAppStartActivities = hashSetOf<Class<out Activity>>()
        doNotEndAppStartActivities.add(DoNotEndAppStartActivity::class.java as Class<out Activity>)
        perfConfig.doNotEndAppStart = doNotEndAppStartActivities
        val autoInstrumentationCache =
            AutoInstrumentationCache()
        autoInstrumentationCache.configuration(perfConfig.doNotEndAppStart, perfConfig.doNotAutoInstrument)

        val isEndAppStartResult1 =
            autoInstrumentationCache.isAppStartActivity(perfConfig.doNotEndAppStart.first())
        assertTrue(isEndAppStartResult1)

        val isEndAppStartResult2 =
            autoInstrumentationCache.isInstrumentationEnabled(perfConfig.doNotEndAppStart.first())
        assertTrue(isEndAppStartResult2)
    }

    @Test
    fun testMixedActivities() {
        val doNotInstrumentActivities = hashSetOf<Class<Any>>()
        val doNotEndAppStartActivities = hashSetOf<Class<out Activity>>()

        doNotInstrumentActivities.add(DoNotInstrumentActivity::class.java as Class<Any>)
        doNotInstrumentActivities.add(NoInstrumentationAppStartActivity::class.java as Class<Any>)

        doNotEndAppStartActivities.add(DoNotEndAppStartActivity::class.java as Class<out Activity>)
        doNotEndAppStartActivities.add(NoInstrumentationAppStartActivity::class.java as Class<out Activity>)

        perfConfig.doNotAutoInstrument = doNotInstrumentActivities
        perfConfig.doNotEndAppStart = doNotEndAppStartActivities

        val autoInstrumentationCache =
            AutoInstrumentationCache()
        autoInstrumentationCache.configuration(perfConfig.doNotEndAppStart, perfConfig.doNotAutoInstrument)

        val isInstrumentationResult1 =
            autoInstrumentationCache.isInstrumentationEnabled(perfConfig.doNotAutoInstrument.last())
        assertTrue(isInstrumentationResult1)

        val isInstrumentationResult2 =
            autoInstrumentationCache.isInstrumentationEnabled(perfConfig.doNotAutoInstrument.first())
        assertTrue(isInstrumentationResult2)

        val isEndAppStartResult1 =
            autoInstrumentationCache.isAppStartActivity(perfConfig.doNotEndAppStart.last())
        assertTrue(isEndAppStartResult1)

        val isEndAppStartResult2 =
            autoInstrumentationCache.isInstrumentationEnabled(perfConfig.doNotEndAppStart.first())
        assertTrue(isEndAppStartResult2)
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
