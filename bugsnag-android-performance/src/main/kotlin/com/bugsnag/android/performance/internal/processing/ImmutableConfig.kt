package com.bugsnag.android.performance.internal.processing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.NetworkRequestInstrumentationCallback
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.OnSpanEndCallback
import com.bugsnag.android.performance.internal.DebugLogger
import com.bugsnag.android.performance.internal.NoopLogger
import com.bugsnag.android.performance.internal.RELEASE_STAGE_PRODUCTION
import com.bugsnag.android.performance.internal.releaseStage
import java.util.regex.Pattern

internal const val DEFAULT_ENDPOINT = "https://otlp.bugsnag.com/v1/traces"

internal data class ImmutableConfig(
    val application: Application,
    val apiKey: String,
    val endpoint: String,
    val autoInstrumentAppStarts: Boolean,
    val autoInstrumentActivities: AutoInstrument,
    val autoInstrumentRendering: Boolean,
    val serviceName: String,
    val releaseStage: String,
    val enabledReleaseStages: Set<String>?,
    val versionCode: Long?,
    val appVersion: String?,
    val logger: Logger,
    val networkRequestCallback: NetworkRequestInstrumentationCallback?,
    val doNotEndAppStart: Collection<Class<out Activity>>,
    val doNotAutoInstrument: Collection<Class<*>>,
    val tracePropagationUrls: Collection<Pattern>,
    val spanEndCallbacks: Array<OnSpanEndCallback>,
    val samplingProbability: Double?,
    override val attributeStringValueLimit: Int,
    override val attributeArrayLengthLimit: Int,
    override val attributeCountLimit: Int,
) : AttributeLimits {
    val isReleaseStageEnabled =
        enabledReleaseStages == null || enabledReleaseStages.contains(releaseStage)

    constructor(configuration: PerformanceConfiguration) : this(
        configuration.context.applicationContext as Application,
        configuration.apiKey.also { validateApiKey(it) },
        configuration.endpoint.takeUnless { it == DEFAULT_ENDPOINT }
            ?: "https://${configuration.apiKey}.otlp.bugsnag.com/v1/traces",
        configuration.autoInstrumentAppStarts,
        configuration.autoInstrumentActivities,
        configuration.autoInstrumentRendering,
        configuration.serviceName ?: configuration.context.packageName,
        getReleaseStage(configuration),
        configuration.enabledReleaseStages?.toSet(),
        configuration.versionCode ?: versionCodeFor(configuration.context),
        configuration.appVersion ?: versionNameFor(configuration.context),
        configuration.logger
            ?: if (getReleaseStage(configuration) == RELEASE_STAGE_PRODUCTION) {
                NoopLogger
            } else {
                DebugLogger
            },
        configuration.networkRequestCallback,
        configuration.doNotEndAppStart,
        configuration.doNotAutoInstrument,
        configuration.tracePropagationUrls.toSet(),
        configuration.spanEndCallbacks.toTypedArray(),
        configuration.samplingProbability,
        configuration.attributeStringValueLimit,
        configuration.attributeArrayLengthLimit,
        configuration.attributeCountLimit,
    )

    companion object {
        private const val VALID_API_KEY_LENGTH = 32

        private fun validateApiKey(apiKey: String) {
            if (apiKey.length != VALID_API_KEY_LENGTH ||
                !apiKey.all { it.isDigit() || it in 'a'..'f' }
            ) {
                Logger.w("Invalid configuration. apiKey should be a 32-character hexademical string, got '$apiKey'")
            }
        }

        private fun getReleaseStage(configuration: PerformanceConfiguration) =
            configuration.releaseStage ?: configuration.context.releaseStage

        private fun versionCodeFor(context: Context): Long? {
            return try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo?.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo?.versionCode?.toLong()
                }
            } catch (ex: RuntimeException) {
                // swallow any exceptions to avoid any possible crash during startup
                null
            }
        }

        private fun versionNameFor(context: Context): String? {
            return try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo?.versionName
            } catch (ex: RuntimeException) {
                // swallow any exceptions to avoid any possible crash during startup
                null
            }
        }
    }
}
