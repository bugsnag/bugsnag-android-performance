package com.bugsnag.android.performance

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import com.bugsnag.android.performance.internal.DEFAULT_ENDPOINT
import java.util.regex.Pattern

public class PerformanceConfiguration private constructor(public val context: Context) {

    public constructor(context: Context, apiKey: String) : this(context) {
        this.apiKey = apiKey
    }

    public var apiKey: String = ""

    public var endpoint: String = DEFAULT_ENDPOINT

    public var autoInstrumentAppStarts: Boolean = true

    public var autoInstrumentActivities: AutoInstrument = AutoInstrument.FULL

    public var releaseStage: String? = null

    public var enabledReleaseStages: Set<String>? = null

    public var versionCode: Long? = null

    public var appVersion: String? = null

    public var serviceName: String? = null

    public var logger: Logger? = null

    public var samplingProbability: Double? = null

    @JvmSynthetic
    internal val spanEndCallbacks: MutableList<SpanEndCallback> = ArrayList()

    /**
     * Activity classes that are considered part of the AppStart and therefore will not
     * end the AppStart when the associated ViewLoad is ended.
     */
    public var doNotEndAppStart: Collection<Class<out Activity>> = HashSet()

    /**
     * A list of classes to not automatically instrument. This will only match the classes
     * exactly (sub-classes must be listed separately).
     */
    public var doNotAutoInstrument: Collection<Class<*>> = HashSet()

    /**
     * Callback to be invoked on every network request that would generate a span.
     * Use this to filter what URLs will generate spans and how.
     */
    public var networkRequestCallback: NetworkRequestInstrumentationCallback? = null

    public var tracePropagationUrls: Collection<Pattern> = HashSet()

    public fun addOnSpanEndCallback(spanEndCallback: SpanEndCallback) {
        spanEndCallbacks.add(spanEndCallback)
    }

    public fun removeOnSpanEndCallback(spanEndCallback: SpanEndCallback) {
        spanEndCallbacks.remove(spanEndCallback)
    }

    override fun toString(): String =
        "PerformanceConfiguration(" +
                "context=$context, " +
                "apiKey=$apiKey, " +
                "endpoint='$endpoint', " +
                "autoInstrumentAppStarts=$autoInstrumentAppStarts, " +
                "autoInstrumentActivities=$autoInstrumentActivities, " +
                "releaseStage=$releaseStage, " +
                "versionCode=$versionCode, " +
                "appVersion=$appVersion, " +
                "enabledReleaseStages=$enabledReleaseStages " +
                "doNotEndAppStart=$doNotEndAppStart " +
                "doNotAutoInstrument=$doNotAutoInstrument " +
                "tracePropagationUrls=$tracePropagationUrls " +
                ")"

    public companion object Loader {

        // mandatory
        private const val BUGSNAG_NS = "com.bugsnag.android"
        private const val BUGSNAG_PERF_NS = "com.bugsnag.performance.android"

        private const val API_KEY = "$BUGSNAG_PERF_NS.API_KEY"
        private const val ENDPOINT_KEY = "$BUGSNAG_PERF_NS.ENDPOINT"
        private const val AUTO_INSTRUMENT_APP_STARTS_KEY =
            "$BUGSNAG_PERF_NS.AUTO_INSTRUMENT_APP_STARTS"
        private const val AUTO_INSTRUMENT_ACTIVITIES_KEY =
            "$BUGSNAG_PERF_NS.AUTO_INSTRUMENT_ACTIVITIES"
        private const val RELEASE_STAGE_KEY = "$BUGSNAG_PERF_NS.RELEASE_STAGE"
        private const val ENABLED_RELEASE_STAGES = "$BUGSNAG_PERF_NS.ENABLED_RELEASE_STAGES"
        private const val VERSION_CODE_KEY = "$BUGSNAG_PERF_NS.VERSION_CODE"
        private const val APP_VERSION_KEY = "$BUGSNAG_PERF_NS.APP_VERSION"
        private const val TRACE_PROPAGATION_URLS_KEY = "$BUGSNAG_PERF_NS.TRACE_PROPAGATION_URLS"
        private const val SERVICE_NAME_KEY = "$BUGSNAG_PERF_NS.SERVICE_NAME"

        // Bugsnag Notifier keys that we can read
        private const val BSG_API_KEY = "$BUGSNAG_NS.API_KEY"
        private const val BSG_RELEASE_STAGE_KEY = "$BUGSNAG_NS.RELEASE_STAGE"
        private const val BSG_VERSION_CODE_KEY = "$BUGSNAG_NS.VERSION_CODE"
        private const val BSG_APP_VERSION_KEY = "$BUGSNAG_NS.APP_VERSION"
        private const val BSG_ENABLED_RELEASE_STAGES = "$BUGSNAG_NS.ENABLED_RELEASE_STAGES"

        @JvmStatic
        @JvmOverloads
        public fun load(ctx: Context, apiKey: String? = null): PerformanceConfiguration {
            try {
                val packageManager = ctx.packageManager
                val packageName = ctx.packageName
                val ai = packageManager.getApplicationInfo(
                    packageName, PackageManager.GET_META_DATA,
                )
                val data = ai.metaData
                return loadFromMetaData(ctx, data, apiKey)
            } catch (exc: Exception) {
                throw IllegalStateException("Bugsnag is unable to read config from manifest.", exc)
            }
        }

        @JvmSynthetic
        @VisibleForTesting
        internal fun loadFromMetaData(
            ctx: Context,
            data: Bundle?,
            apiKeyOverride: String?,
        ): PerformanceConfiguration {
            val config = PerformanceConfiguration(ctx)

            (apiKeyOverride ?: data?.getString(API_KEY, data.getString(BSG_API_KEY)))
                ?.also { config.apiKey = it }

            if (data != null) {
                data.getString(ENDPOINT_KEY)
                    ?.also { config.endpoint = it }

                config.autoInstrumentAppStarts = data.getBoolean(
                    AUTO_INSTRUMENT_APP_STARTS_KEY,
                    config.autoInstrumentAppStarts,
                )

                data.getString(AUTO_INSTRUMENT_ACTIVITIES_KEY)
                    ?.also { config.autoInstrumentActivities = AutoInstrument.valueOf(it) }

                // releaseStage / enabledReleaseStage
                data.getString(RELEASE_STAGE_KEY, data.getString(BSG_RELEASE_STAGE_KEY))
                    ?.also { config.releaseStage = it }
                data.getString(ENABLED_RELEASE_STAGES, data.getString(BSG_ENABLED_RELEASE_STAGES))
                    ?.also { config.enabledReleaseStages = it.splitToSequence(',').toSet() }

                if (data.containsKey(VERSION_CODE_KEY)) {
                    config.versionCode = data.getInt(VERSION_CODE_KEY).toLong()
                } else if (data.containsKey(BSG_VERSION_CODE_KEY)) {
                    config.versionCode = data.getInt(BSG_VERSION_CODE_KEY).toLong()
                }

                if (data.containsKey(APP_VERSION_KEY)) {
                    config.appVersion = data.getString(APP_VERSION_KEY)
                } else if (data.containsKey(BSG_APP_VERSION_KEY)) {
                    config.appVersion = data.getString(BSG_APP_VERSION_KEY)
                }

                if (data.containsKey(TRACE_PROPAGATION_URLS_KEY)) {
                    config.tracePropagationUrls = data.getString(TRACE_PROPAGATION_URLS_KEY)
                        ?.splitToSequence(',')
                        ?.map { it.toPattern() }
                        ?.toList()
                        .orEmpty()
                }

                data.getString(SERVICE_NAME_KEY)
                    ?.also { config.serviceName = it }
            }

            return config
        }
    }
}
