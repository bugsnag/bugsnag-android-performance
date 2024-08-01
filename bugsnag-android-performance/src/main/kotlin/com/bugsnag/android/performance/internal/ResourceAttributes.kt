package com.bugsnag.android.performance.internal

import android.os.Build
import com.bugsnag.android.performance.BugsnagPerformance

internal fun createResourceAttributes(configuration: ImmutableConfig): Attributes {
    val resourceAttributes = Attributes()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        resourceAttributes["host.arch"] = abiToArchitecture(Build.SUPPORTED_ABIS.firstOrNull())
    } else {
        @Suppress("DEPRECATION")
        resourceAttributes["host.arch"] = abiToArchitecture(Build.CPU_ABI)
    }

    resourceAttributes["os.type"] = "linux"
    resourceAttributes["os.name"] = "android"
    resourceAttributes["os.version"] = Build.VERSION.RELEASE
    resourceAttributes["bugsnag.device.android_api_version"] = Build.VERSION.SDK_INT.toString()

    resourceAttributes["device.model.identifier"] = Build.MODEL
    resourceAttributes["device.manufacturer"] = Build.MANUFACTURER
    resourceAttributes["deployment.environment"] = configuration.releaseStage

    resourceAttributes["bugsnag.app.version_code"] = configuration.versionCode.toString()

    resourceAttributes["service.name"] = configuration.serviceName
    resourceAttributes["service.version"] = configuration.appVersion
    resourceAttributes["telemetry.sdk.name"] = "bugsnag.performance.android"
    resourceAttributes["telemetry.sdk.version"] = BugsnagPerformance.VERSION

    return resourceAttributes
}

private fun abiToArchitecture(abi: String?): String? = when (abi?.lowercase()) {
    "arm64-v8a" -> "arm64"
    "x86_64" -> "amd64"
    "armeabi-v7a" -> "arm32"
    "x86" -> "x86"
    else -> null
}
