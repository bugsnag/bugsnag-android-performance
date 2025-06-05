package com.bugsnag.android.performance.internal

import android.app.Application
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.EnabledMetrics
import com.bugsnag.android.performance.internal.processing.ImmutableConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class ResourceAttributesTest {
    @Test
    fun testAttributeDefaults() {
        val application = configureApplication()

        val configuration =
            ImmutableConfig(
                application,
                "decafbaddecafbaddecafbaddecafbad",
                "",
                true,
                AutoInstrument.FULL,
                EnabledMetrics(true, true, true),
                "bugsnag.performance.android",
                "development",
                setOf("production"),
                321L,
                "6.5.4",
                NoopLogger,
                null,
                emptySet(),
                emptySet(),
                emptySet(),
                emptyArray(),
                emptyArray(),
                null,
                1024,
                1000,
                100,
            )

        val attributes = createResourceAttributes(configuration)

        assertTrue(attributes["host.arch"] in setOf("amd64", "arm64", "arm32", "x86"))
        assertEquals("linux", attributes["os.type"])
        assertEquals("android", attributes["os.name"])
        assertEquals("13", attributes["os.version"])
        assertEquals("33", attributes["bugsnag.device.android_api_version"])
        assertEquals("TEST-1234", attributes["device.model.identifier"])
        assertEquals("Bugsnag", attributes["device.manufacturer"])
        assertEquals("development", attributes["deployment.environment"])
        assertEquals("321", attributes["bugsnag.app.version_code"])
        assertEquals("6.5.4", attributes["service.version"])
        assertEquals("bugsnag.performance.android", attributes["service.name"])
        assertEquals("bugsnag.performance.android", attributes["telemetry.sdk.name"])
    }

    @Test
    fun testAttributeOverrides() {
        val application = configureApplication()

        val configuration =
            ImmutableConfig(
                application,
                "decafbaddecafbaddecafbaddecafbad",
                "",
                true,
                AutoInstrument.FULL,
                EnabledMetrics(true, true, true),
                "bugsnag.performance.android",
                "production",
                setOf("production"),
                123L,
                "5.4.3",
                NoopLogger,
                null,
                emptySet(),
                emptySet(),
                emptySet(),
                emptyArray(),
                emptyArray(),
                null,
                1024,
                1000,
                100,
            )

        val attributes = createResourceAttributes(configuration)

        assertTrue(attributes["host.arch"] in setOf("amd64", "arm64", "arm32", "x86"))
        assertEquals("linux", attributes["os.type"])
        assertEquals("android", attributes["os.name"])
        assertEquals("13", attributes["os.version"])
        assertEquals("33", attributes["bugsnag.device.android_api_version"])
        assertEquals("TEST-1234", attributes["device.model.identifier"])
        assertEquals("Bugsnag", attributes["device.manufacturer"])
        assertEquals("production", attributes["deployment.environment"]) // overridden
        assertEquals("123", attributes["bugsnag.app.version_code"]) // overridden
        assertEquals("5.4.3", attributes["service.version"])
        assertEquals("bugsnag.performance.android", attributes["service.name"])
        assertEquals("bugsnag.performance.android", attributes["telemetry.sdk.name"])
    }

    private fun configureApplication(): Application {
        ShadowBuild.setManufacturer("Bugsnag")
        ShadowBuild.setModel("TEST-1234")

        val application = RuntimeEnvironment.getApplication()

        shadowOf(application.packageManager)
            .getInternalMutablePackageInfo(application.packageName)
            .longVersionCode = 321L

        return application
    }
}
