package com.bugsnag.android.performance

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.annotation.FloatRange
import androidx.annotation.VisibleForTesting
import com.bugsnag.android.performance.internal.RELEASE_STAGE_PRODUCTION

class PerformanceConfiguration private constructor(val context: Context) {

    constructor(context: Context, apiKey: String) : this(context) {
        this.apiKey = apiKey
    }

    var apiKey: String = ""

    var endpoint: String = "https://otlp.bugsnag.com/v1/traces"

    var autoInstrumentAppStarts = true

    var autoInstrumentActivities = AutoInstrument.FULL

    var releaseStage: String? = null

    var enabledReleaseStages: Set<String> = mutableSetOf(RELEASE_STAGE_PRODUCTION)

    var versionCode: Long? = null

    @FloatRange(from = 0.0, to = 1.0)
    var samplingProbability: Double = 1.0
        set(value) {
            require(value in 0.0..1.0) { "samplingProbability out of range (0..1): $value" }
            field = value
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
            "enabledReleaseStages=$enabledReleaseStages, " +
            "samplingProbability=$samplingProbability" +
            ")"

    companion object Loader {

        // mandatory
        private const val BUGSNAG_NS = "com.bugsnag.android"
        private const val BUGSNAG_PERF_NS = "com.bugsnag.performance.android"
        private const val API_KEY = "$BUGSNAG_NS.API_KEY"

        private const val ENDPOINT_KEY = "$BUGSNAG_PERF_NS.ENDPOINT"
        private const val AUTO_INSTRUMENT_APP_STARTS_KEY =
            "$BUGSNAG_PERF_NS.AUTO_INSTRUMENT_APP_STARTS"
        private const val AUTO_INSTRUMENT_ACTIVITIES_KEY =
            "$BUGSNAG_PERF_NS.AUTO_INSTRUMENT_ACTIVITIES"
        private const val RELEASE_STAGE_KEY = "$BUGSNAG_PERF_NS.RELEASE_STAGE"
        private const val VERSION_CODE_KEY = "$BUGSNAG_PERF_NS.VERSION_CODE"

        // Bugsnag Notifier keys that we can read
        private const val BSG_RELEASE_STAGE_KEY = "$BUGSNAG_NS.RELEASE_STAGE"
        private const val BSG_VERSION_CODE_KEY = "$BUGSNAG_NS.VERSION_CODE"

        @JvmStatic
        fun load(ctx: Context, apiKey: String? = null): PerformanceConfiguration {
            try {
                val packageManager = ctx.packageManager
                val packageName = ctx.packageName
                val ai = packageManager.getApplicationInfo(
                    packageName, PackageManager.GET_META_DATA
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
            apiKeyOverride: String?
        ): PerformanceConfiguration {
            return PerformanceConfiguration(ctx).apply {
                (apiKeyOverride ?: data?.getString(API_KEY))
                    ?.also { apiKey = it }

                data?.getString(ENDPOINT_KEY)
                    ?.also { endpoint = it }
                data?.getBoolean(AUTO_INSTRUMENT_APP_STARTS_KEY, autoInstrumentAppStarts)
                    ?.also { autoInstrumentAppStarts = it }
                data?.getString(AUTO_INSTRUMENT_ACTIVITIES_KEY)
                    ?.also { autoInstrumentActivities = AutoInstrument.valueOf(it) }

                data?.getString(RELEASE_STAGE_KEY, data.getString(BSG_RELEASE_STAGE_KEY))
                    ?.also { releaseStage = it }

                if (data?.containsKey(VERSION_CODE_KEY) == true) {
                    versionCode = data.getInt(VERSION_CODE_KEY).toLong()
                } else if (data?.containsKey(BSG_VERSION_CODE_KEY) == true) {
                    versionCode = data.getInt(BSG_VERSION_CODE_KEY).toLong()
                }
            }
        }
    }
}
