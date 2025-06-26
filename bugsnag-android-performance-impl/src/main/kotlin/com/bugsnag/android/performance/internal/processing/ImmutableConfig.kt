package com.bugsnag.android.performance.internal.processing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.EnabledMetrics
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.NetworkRequestInstrumentationCallback
import com.bugsnag.android.performance.OnSpanEndCallback
import com.bugsnag.android.performance.OnSpanStartCallback
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.PerformanceConfiguration.Loader.DEFAULT_ENDPOINT
import com.bugsnag.android.performance.PluginContext
import com.bugsnag.android.performance.internal.DebugLogger
import com.bugsnag.android.performance.internal.NoopLogger
import com.bugsnag.android.performance.internal.RELEASE_STAGE_PRODUCTION
import com.bugsnag.android.performance.internal.plugins.PluginManager
import com.bugsnag.android.performance.internal.releaseStage
import com.bugsnag.android.performance.internal.util.Prioritized
import java.util.regex.Pattern

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ImmutableConfig(
    public val application: Application,
    public val apiKey: String,
    public val endpoint: String,
    public val autoInstrumentAppStarts: Boolean,
    public val autoInstrumentActivities: AutoInstrument,
    public val enabledMetrics: EnabledMetrics,
    public val serviceName: String,
    public val releaseStage: String,
    public val enabledReleaseStages: Set<String>?,
    public val versionCode: Long?,
    public val appVersion: String?,
    public val networkRequestCallback: NetworkRequestInstrumentationCallback?,
    public val doNotEndAppStart: Collection<Class<out Activity>>,
    public val doNotAutoInstrument: Collection<Class<*>>,
    public val tracePropagationUrls: Collection<Pattern>,
    public val spanStartCallbacks: List<Prioritized<OnSpanStartCallback>>,
    public val spanEndCallbacks: List<Prioritized<OnSpanEndCallback>>,
    public val samplingProbability: Double?,
    override val attributeStringValueLimit: Int,
    override val attributeArrayLengthLimit: Int,
    override val attributeCountLimit: Int,
) : AttributeLimits {
    public val isReleaseStageEnabled: Boolean =
        enabledReleaseStages == null || enabledReleaseStages.contains(releaseStage)

    public constructor(
        configuration: PerformanceConfiguration,
        pluginManager: PluginManager,
    ) : this(
        configuration.context.applicationContext as Application,
        configuration.apiKey.also { validateApiKey(it) },
        if (configuration.endpoint == DEFAULT_ENDPOINT) {
            if (configuration.apiKey.startsWith(HUB_API_PREFIX)) {
                HUB_ENDPOINT.format(configuration.apiKey)
            } else {
                BUGSNAG_ENDPOINT.format(configuration.apiKey)
            }
        } else {
            configuration.endpoint
        },
        configuration.autoInstrumentAppStarts,
        configuration.autoInstrumentActivities,
        configuration.enabledMetrics.copy(),
        configuration.serviceName ?: configuration.context.packageName,
        configuration.releaseStage ?: configuration.context.releaseStage,
        configuration.enabledReleaseStages?.toSet(),
        configuration.versionCode ?: versionCodeFor(configuration.context),
        configuration.appVersion ?: versionNameFor(configuration.context),
        configuration.networkRequestCallback,
        configuration.doNotEndAppStart,
        configuration.doNotAutoInstrument,
        configuration.tracePropagationUrls.toSet(),
        createPrioritizedList(
            configuration.spanStartCallbacks,
            pluginManager.completeContext?.spanStartCallbacks.orEmpty(),
        ),
        createPrioritizedList(
            configuration.spanEndCallbacks,
            pluginManager.completeContext?.spanEndCallbacks.orEmpty(),
        ),
        configuration.samplingProbability,
        configuration.attributeStringValueLimit,
        configuration.attributeArrayLengthLimit,
        configuration.attributeCountLimit,
    )


    internal companion object {
        private const val VALID_API_KEY_LENGTH = 32

        private const val BUGSNAG_ENDPOINT = "https://%s.otlp.bugsnag.com/v1/traces"
        private const val HUB_ENDPOINT = "https://%s.otlp.insighthub.smartbear.com/v1/traces"
        private const val HUB_API_PREFIX = "00000"

        internal fun EnabledMetrics.copy() = EnabledMetrics(rendering, cpu, memory)

        internal fun <T> createPrioritizedList(
            normalPriority: Collection<T>,
            prioritized: Collection<Prioritized<T>>,
        ): List<Prioritized<T>> {
            val out = ArrayList<Prioritized<T>>(normalPriority.size + prioritized.size)
            normalPriority.mapTo(out) { Prioritized(PluginContext.NORM_PRIORITY, it) }
            out.addAll(prioritized)
            return out
        }

        fun selectEndpoint(endpointUri: String, apiKey: String): String {
            return if (endpointUri == DEFAULT_ENDPOINT) {
                if (apiKey.startsWith(HUB_API_PREFIX)) {
                    HUB_ENDPOINT.format(apiKey)
                } else {
                    BUGSNAG_ENDPOINT.format(apiKey)
                }
            } else {
                endpointUri
            }
        }

        internal fun getLogger(configuration: PerformanceConfiguration): Logger {
            return configuration.logger
                ?: if (configuration.releaseStage == RELEASE_STAGE_PRODUCTION) {
                    NoopLogger
                } else {
                    DebugLogger
                }
        }

        fun validateApiKey(apiKey: String) {
            if (apiKey.length != VALID_API_KEY_LENGTH ||
                !apiKey.all { it.isDigit() || it in 'a'..'f' }
            ) {
                Logger.w("Invalid configuration. apiKey should be a 32-character hexademical string, got '$apiKey'")
            }
        }

        fun versionCodeFor(context: Context): Long? {
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

        fun versionNameFor(context: Context): String? {
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
