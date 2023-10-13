package com.bugsnag.android.performance

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.annotation.VisibleForTesting

class PerformanceConfiguration private constructor(val context: Context) {

    constructor(context: Context, apiKey: String) : this(context) {
        this.apiKey = apiKey
    }

    var apiKey: String = ""

    var endpoint: String = "https://otlp.bugsnag.com/v1/traces"

    var autoInstrumentAppStarts = true

    var autoInstrumentActivities = AutoInstrument.FULL

    var releaseStage: String? = null

    var enabledReleaseStages: Set<String>? = null

    var versionCode: Long? = null

    var appVersion: String? = null

    var logger: Logger? = null

    /**
     * Activity classes that are considered part of the AppStart and therefore will not
     * end the AppStart when the associated ViewLoad is ended.
     */
    var doNotEndAppStart: Collection<Class<out Activity>> = HashSet()

    /**
     * A list of classes to not automatically instrument. This will only match the classes
     * exactly (sub-classes must be listed separately).
     */
    var doNotAutoInstrument: Collection<Class<*>> = HashSet()

    /**
     * Callback to be invoked on every network request that would generate a span.
     * Use this to filter what URLs will generate spans and how.
     */
    var networkRequestCallback: NetworkRequestInstrumentationCallback? = null

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
                ")"

    companion object Loader {

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

        // Bugsnag Notifier keys that we can read
        private const val BSG_API_KEY = "$BUGSNAG_NS.API_KEY"
        private const val BSG_RELEASE_STAGE_KEY = "$BUGSNAG_NS.RELEASE_STAGE"
        private const val BSG_VERSION_CODE_KEY = "$BUGSNAG_NS.VERSION_CODE"
        private const val BSG_APP_VERSION_KEY = "$BUGSNAG_NS.APP_VERSION"
        private const val BSG_ENABLED_RELEASE_STAGES = "$BUGSNAG_NS.ENABLED_RELEASE_STAGES"

        @JvmStatic
        @JvmOverloads
        fun load(ctx: Context, apiKey: String? = null): PerformanceConfiguration {
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
            return PerformanceConfiguration(ctx).apply {
                (apiKeyOverride ?: data?.getString(API_KEY, data.getString(BSG_API_KEY)))
                    ?.also { apiKey = it }

                data?.getString(ENDPOINT_KEY)
                    ?.also { endpoint = it }
                data?.getBoolean(AUTO_INSTRUMENT_APP_STARTS_KEY, autoInstrumentAppStarts)
                    ?.also { autoInstrumentAppStarts = it }
                data?.getString(AUTO_INSTRUMENT_ACTIVITIES_KEY)
                    ?.also { autoInstrumentActivities = AutoInstrument.valueOf(it) }

                // releaseStage / enabledReleaseStage
                data?.getString(RELEASE_STAGE_KEY, data.getString(BSG_RELEASE_STAGE_KEY))
                    ?.also { releaseStage = it }
                data?.getString(ENABLED_RELEASE_STAGES, data.getString(BSG_ENABLED_RELEASE_STAGES))
                    ?.also { enabledReleaseStages = it.splitToSequence(',').toSet() }

                if (data?.containsKey(VERSION_CODE_KEY) == true) {
                    versionCode = data.getInt(VERSION_CODE_KEY).toLong()
                } else if (data?.containsKey(BSG_VERSION_CODE_KEY) == true) {
                    versionCode = data.getInt(BSG_VERSION_CODE_KEY).toLong()
                }

                if (data?.containsKey(APP_VERSION_KEY) == true) {
                    appVersion = data.getString(APP_VERSION_KEY)
                } else if (data?.containsKey(BSG_APP_VERSION_KEY) == true) {
                    appVersion = data.getString(BSG_APP_VERSION_KEY)
                }
            }
        }
    }
}
