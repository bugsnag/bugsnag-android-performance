package com.bugsnag.android.performance.internal

import android.app.Application
import android.os.Build
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.test.setStatic
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

@Config(sdk = [32])
@RunWith(RobolectricTestRunner::class)
class ResourceAttributesTest {
    @Test
    fun testAttributeDefaults() {
        val application = configureApplication()

        val configuration = ImmutableConfig(
            application,
            "decafbaddecafbaddecafbaddecafbad",
            "",
            true,
            AutoInstrument.FULL,
            "bugsnag.performance.android",
            "development",
            setOf("production"),
            321L,
            1.0,
            NoopLogger,
        )

        val attributes = createResourceAttributes(configuration).toList().toMap()

        assertEquals("amd64", attributes["host.arch"])
        assertEquals("linux", attributes["os.type"])
        assertEquals("android", attributes["os.name"])
        assertEquals("33", attributes["os.version"])
        assertEquals("TEST-1234", attributes["device.model.identifier"])
        assertEquals("Bugsnag", attributes["device.manufacturer"])
        assertEquals("development", attributes["deployment.environment"])
        assertEquals("321", attributes["bugsnag.app.version_code"])
        assertEquals("bugsnag.performance.android", attributes["service.name"])
        assertEquals("bugsnag.performance.android", attributes["telemetry.sdk.name"])
    }

    @Test
    fun testAttributeOverrides() {
        val application = configureApplication()

        val configuration = ImmutableConfig(
            application,
            "decafbaddecafbaddecafbaddecafbad",
            "",
            true,
            AutoInstrument.FULL,
            "bugsnag.performance.android",
            "production",
            setOf("production"),
            123L,
            1.0,
            NoopLogger,
        )

        val attributes = createResourceAttributes(configuration).toList().toMap()

        assertEquals("amd64", attributes["host.arch"])
        assertEquals("linux", attributes["os.type"])
        assertEquals("android", attributes["os.name"])
        assertEquals("33", attributes["os.version"])
        assertEquals("TEST-1234", attributes["device.model.identifier"])
        assertEquals("Bugsnag", attributes["device.manufacturer"])
        assertEquals("production", attributes["deployment.environment"]) // overridden
        assertEquals("123", attributes["bugsnag.app.version_code"]) // overridden
        assertEquals("bugsnag.performance.android", attributes["service.name"])
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
