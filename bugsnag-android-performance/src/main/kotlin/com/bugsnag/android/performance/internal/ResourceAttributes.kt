package com.bugsnag.android.performance.internal

import android.content.Context
import android.os.Build
import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration

internal fun createResourceAttributes(configuration: PerformanceConfiguration): Attributes {
    val resourceAttributes = Attributes()
    val context = configuration.context

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        resourceAttributes["host.arch"] = abiToArchitecture(Build.SUPPORTED_ABIS.firstOrNull())
    } else {
        @Suppress("DEPRECATION")
        resourceAttributes["host.arch"] = abiToArchitecture(Build.CPU_ABI)
    }

    resourceAttributes["os.type"] = "linux"
    resourceAttributes["os.name"] = "android"
    resourceAttributes["os.version"] = Build.VERSION.SDK_INT.toString()

    resourceAttributes["device.model.identifier"] = Build.MODEL
    resourceAttributes["device.manufacturer"] = Build.MANUFACTURER
    resourceAttributes["deployment.environment"] =
        configuration.releaseStage ?: context.releaseStage

    configuration.versionCodeFor(context)?.let { versionCode ->
        resourceAttributes["bugsnag.app.version_code"] = versionCode.toString()
    }

    resourceAttributes["service.name"] = configuration.context.packageName
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

private fun PerformanceConfiguration.versionCodeFor(context: Context): Long? {
    if (versionCode != null) return versionCode
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toLong()
        }
    } catch (ex: RuntimeException) {
        null
    }
}
