package com.bugsnag.android.performance.internal

import android.app.Application
import android.os.Build
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.test.setStatic
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
class ResourceAttributesTest {
    @Test
    fun testAttributeDefaults() {
        val application = configureApplication()

        val configuration = PerformanceConfiguration(application)
        val attributes = createResourceAttributes(configuration).toList().toMap()

        assertEquals("amd64", attributes["host.arch"])
        assertEquals("linux", attributes["os.type"])
        assertEquals("android", attributes["os.name"])
        assertEquals("33", attributes["os.version"])
        assertEquals("TEST-1234", attributes["device.model.identifier"])
        assertEquals("Bugsnag", attributes["device.manufacturer"])
        assertEquals("development", attributes["deployment.environment"])
        assertEquals(321L, attributes["bugsnag.app.version_code"])
        assertEquals(application.packageName, attributes["service.name"])
        assertEquals("bugsnag.performance.android", attributes["telemetry.sdk.name"])
    }

    @Test
    fun testAttributeOverrides() {
        val application = configureApplication()

        val configuration = PerformanceConfiguration(application).apply {
            versionCode = 123
            releaseStage = "production"
        }

        val attributes = createResourceAttributes(configuration).toList().toMap()

        assertEquals("amd64", attributes["host.arch"])
        assertEquals("linux", attributes["os.type"])
        assertEquals("android", attributes["os.name"])
        assertEquals("33", attributes["os.version"])
        assertEquals("TEST-1234", attributes["device.model.identifier"])
        assertEquals("Bugsnag", attributes["device.manufacturer"])
        assertEquals("production", attributes["deployment.environment"]) // overridden
        assertEquals(123L, attributes["bugsnag.app.version_code"]) // overridden
        assertEquals(application.packageName, attributes["service.name"])
        assertEquals("bugsnag.performance.android", attributes["telemetry.sdk.name"])
    }

    private fun configureApplication(): Application {
        ShadowBuild.setManufacturer("Bugsnag")
        ShadowBuild.setModel("TEST-1234")

        setStatic(Build::SUPPORTED_ABIS, arrayOf("x86_64", "arm64-v8a"))
        setStatic(Build.VERSION::SDK_INT, 33)

        val application = RuntimeEnvironment.getApplication()

        shadowOf(application.packageManager)
            .getInternalMutablePackageInfo(application.packageName)
            .longVersionCode = 321L

        return application
    }
}
