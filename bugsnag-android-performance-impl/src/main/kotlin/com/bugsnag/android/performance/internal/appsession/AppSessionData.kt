package com.bugsnag.android.performance.internal.appsession

import org.json.JSONObject

/**
 * Immutable snapshot of a single completed app session.
 *
 * Instances are held in [AppSessionBuffer] immediately after their span ends.
 * They are periodically persisted to disk by the buffer so that in-progress app sessions
 * are not lost if the process is killed before delivery.
 *
 * Each app session maps 1:1 with an `app_session` span
 * that has already been sent via the Tracer, but a copy is kept here for:
 *   - Local querying / dashboards (e.g. "show me last 10 sessions")
 *   - Re-delivery if the initial batch delivery failed
 *   - Cross-session analytics in the app itself
 */
internal data class AppSessionData(
    /** Shared identifier for all app sessions in the same logical session. */
    internal val sessionId: String,
    /** 1-based monotonic index of this app session within the logical session. */
    internal val index: Int,
    /**
     * Optional human-readable label supplied by the caller, e.g. `"checkout_flow"`.
     * Stored only in internal app-session persistence for local diagnostics / recovery.
     */
    internal val appSessionName: String?,
    /** Wall-clock milliseconds (Unix epoch) when the app session started. */
    internal val startTimeMs: Long,
    /** Wall-clock nanoseconds (Unix epoch) when the app session started. */
    internal val startTimeUnixNano: Long = startTimeMs * NANOS_IN_MILLI,
    /** Wall-clock milliseconds (Unix epoch) when the app session ended. */
    internal val endTimeMs: Long,
    /** Wall-clock nanoseconds (Unix epoch) when the app session ended. */
    internal val endTimeUnixNano: Long = endTimeMs * NANOS_IN_MILLI,
    /** `endTimeMs - startTimeMs` in milliseconds. */
    internal val durationMs: Long,
    /**
     * Why this app session was closed.
     * One of: `state_switched`, `client_end_foreground`, `client_end_background`,
     *         `background_timeout`, `session_max_duration`, `sdk_stopped`.
     */
    internal val closeReason: String,
    // ── Metrics ──────────────────────────────────────────────────────────────
    internal val cpuCount: Int = 0,
    internal val cpuMin: Double = 0.0,
    internal val cpuMax: Double = 0.0,
    internal val cpuMean: Double = 0.0,
    internal val runtimeMemoryCount: Int = 0,
    internal val runtimeMemoryMinBytes: Long = 0L,
    internal val runtimeMemoryMaxBytes: Long = 0L,
    internal val runtimeMemoryMeanBytes: Long = 0L,
    /**
     * ART heap aliases for the runtime memory aggregates.
     * These mirror the runtime fields so the persisted app-session payload can expose
     * the same naming as the span attributes owned by `MemoryMetricsSource`.
     */
    internal val artMemoryCount: Int = runtimeMemoryCount,
    internal val artMemoryMinBytes: Long = runtimeMemoryMinBytes,
    internal val artMemoryMaxBytes: Long = runtimeMemoryMaxBytes,
    internal val artMemoryMeanBytes: Long = runtimeMemoryMeanBytes,
    internal val deviceMemoryCount: Int = 0,
    internal val deviceMemoryMinBytes: Long = 0L,
    internal val deviceMemoryMaxBytes: Long = 0L,
    internal val deviceMemoryMeanBytes: Long = 0L,
) {
    // ─────────────────────────────────────────────────────────────────────────
    // Serialisation helpers
    // ─────────────────────────────────────────────────────────────────────────

    internal fun toJson(): JSONObject =
        JSONObject().apply {
            put(KEY_SESSION_ID, sessionId)
            put(KEY_INDEX, index)
            putOpt(KEY_APP_SESSION_NAME, appSessionName)
            put(KEY_START_TIME_MS, startTimeMs)
            put(KEY_START_TIME_UNIX_NANO, startTimeUnixNano)
            put(KEY_END_TIME_MS, endTimeMs)
            put(KEY_END_TIME_UNIX_NANO, endTimeUnixNano)
            put(KEY_DURATION_MS, durationMs)
            put(KEY_CLOSE_REASON, closeReason)

            if (cpuCount > 0) {
                put(KEY_CPU_COUNT, cpuCount)
                put(KEY_CPU_MIN, cpuMin)
                put(KEY_CPU_MAX, cpuMax)
                put(KEY_CPU_MEAN, cpuMean)
            }
            if (runtimeMemoryCount > 0) {
                put(KEY_RT_MEM_COUNT, runtimeMemoryCount)
                put(KEY_RT_MEM_MIN, runtimeMemoryMinBytes)
                put(KEY_RT_MEM_MAX, runtimeMemoryMaxBytes)
                put(KEY_RT_MEM_MEAN, runtimeMemoryMeanBytes)
            }
            if (artMemoryCount > 0) {
                put(KEY_ART_MEM_COUNT, artMemoryCount)
                put(KEY_ART_MEM_MIN, artMemoryMinBytes)
                put(KEY_ART_MEM_MAX, artMemoryMaxBytes)
                put(KEY_ART_MEM_MEAN, artMemoryMeanBytes)
            }
            if (deviceMemoryCount > 0) {
                put(KEY_DEV_MEM_COUNT, deviceMemoryCount)
                put(KEY_DEV_MEM_MIN, deviceMemoryMinBytes)
                put(KEY_DEV_MEM_MAX, deviceMemoryMaxBytes)
                put(KEY_DEV_MEM_MEAN, deviceMemoryMeanBytes)
            }
        }

    internal fun toMap(): Map<String, Any?> =
        mutableMapOf<String, Any?>(
            "sessionId" to sessionId,
            "index" to index,
            "appSessionName" to appSessionName,
            "startTimeMs" to startTimeMs,
            "startTimeUnixNano" to startTimeUnixNano,
            "endTimeMs" to endTimeMs,
            "endTimeUnixNano" to endTimeUnixNano,
            "durationMs" to durationMs,
            "closeReason" to closeReason,
        ).apply {
            if (cpuCount > 0) {
                put("cpuCount", cpuCount)
                put("cpuMin", cpuMin)
                put("cpuMax", cpuMax)
                put("cpuMean", cpuMean)
            }
            if (runtimeMemoryCount > 0) {
                put("runtimeMemoryCount", runtimeMemoryCount)
                put("runtimeMemoryMinBytes", runtimeMemoryMinBytes)
                put("runtimeMemoryMaxBytes", runtimeMemoryMaxBytes)
                put("runtimeMemoryMeanBytes", runtimeMemoryMeanBytes)
            }
            if (deviceMemoryCount > 0) {
                put("deviceMemoryCount", deviceMemoryCount)
                put("deviceMemoryMinBytes", deviceMemoryMinBytes)
                put("deviceMemoryMaxBytes", deviceMemoryMaxBytes)
                put("deviceMemoryMeanBytes", deviceMemoryMeanBytes)
            }
        }

    internal companion object {
        // ── JSON keys ─────────────────────────────────────────────────────────
        private const val KEY_SESSION_ID = "sessionId"
        private const val KEY_INDEX = "index"
        private const val KEY_APP_SESSION_NAME = "appSessionName"
        private const val KEY_APP_SESSION_NAME_LEGACY = "segmentName"
        private const val KEY_START_TIME_MS = "startTimeMs"
        private const val KEY_START_TIME_UNIX_NANO = "startTimeUnixNano"
        private const val KEY_END_TIME_MS = "endTimeMs"
        private const val KEY_END_TIME_UNIX_NANO = "endTimeUnixNano"
        private const val KEY_DURATION_MS = "durationMs"
        private const val KEY_CLOSE_REASON = "closeReason"

        private const val NANOS_IN_MILLI = 1_000_000L

        private const val KEY_CPU_COUNT = "cpuCount"
        private const val KEY_CPU_MIN = "cpuMin"
        private const val KEY_CPU_MAX = "cpuMax"
        private const val KEY_CPU_MEAN = "cpuMean"

        private const val KEY_RT_MEM_COUNT = "runtimeMemoryCount"
        private const val KEY_RT_MEM_MIN = "runtimeMemoryMinBytes"
        private const val KEY_RT_MEM_MAX = "runtimeMemoryMaxBytes"
        private const val KEY_RT_MEM_MEAN = "runtimeMemoryMeanBytes"

        private const val KEY_ART_MEM_COUNT = "artMemoryCount"
        private const val KEY_ART_MEM_MIN = "artMemoryMinBytes"
        private const val KEY_ART_MEM_MAX = "artMemoryMaxBytes"
        private const val KEY_ART_MEM_MEAN = "artMemoryMeanBytes"

        private const val KEY_DEV_MEM_COUNT = "deviceMemoryCount"
        private const val KEY_DEV_MEM_MIN = "deviceMemoryMinBytes"
        private const val KEY_DEV_MEM_MAX = "deviceMemoryMaxBytes"
        private const val KEY_DEV_MEM_MEAN = "deviceMemoryMeanBytes"

        internal fun fromJson(json: JSONObject): AppSessionData =
            AppSessionData(
                sessionId = json.getString(KEY_SESSION_ID),
                index = json.optInt(KEY_INDEX, json.optInt("segmentIndex")),
                appSessionName =
                    json.optString(KEY_APP_SESSION_NAME)
                        .takeIf { it.isNotEmpty() }
                        ?: json.optString(KEY_APP_SESSION_NAME_LEGACY).takeIf { it.isNotEmpty() },
                startTimeMs = json.getLong(KEY_START_TIME_MS),
                startTimeUnixNano =
                    json.optLong(
                        KEY_START_TIME_UNIX_NANO,
                        json.getLong(KEY_START_TIME_MS) * NANOS_IN_MILLI,
                    ),
                endTimeMs = json.getLong(KEY_END_TIME_MS),
                endTimeUnixNano =
                    json.optLong(
                        KEY_END_TIME_UNIX_NANO,
                        json.getLong(KEY_END_TIME_MS) * NANOS_IN_MILLI,
                    ),
                durationMs = json.getLong(KEY_DURATION_MS),
                closeReason = json.getString(KEY_CLOSE_REASON),
                cpuCount = json.optInt(KEY_CPU_COUNT),
                cpuMin = json.optDouble(KEY_CPU_MIN),
                cpuMax = json.optDouble(KEY_CPU_MAX),
                cpuMean = json.optDouble(KEY_CPU_MEAN),
                runtimeMemoryCount = json.optInt(KEY_RT_MEM_COUNT),
                runtimeMemoryMinBytes = json.optLong(KEY_RT_MEM_MIN),
                runtimeMemoryMaxBytes = json.optLong(KEY_RT_MEM_MAX),
                runtimeMemoryMeanBytes = json.optLong(KEY_RT_MEM_MEAN),
                artMemoryCount = json.optInt(KEY_ART_MEM_COUNT, json.optInt(KEY_RT_MEM_COUNT)),
                artMemoryMinBytes = json.optLong(KEY_ART_MEM_MIN, json.optLong(KEY_RT_MEM_MIN)),
                artMemoryMaxBytes = json.optLong(KEY_ART_MEM_MAX, json.optLong(KEY_RT_MEM_MAX)),
                artMemoryMeanBytes = json.optLong(KEY_ART_MEM_MEAN, json.optLong(KEY_RT_MEM_MEAN)),
                deviceMemoryCount = json.optInt(KEY_DEV_MEM_COUNT),
                deviceMemoryMinBytes = json.optLong(KEY_DEV_MEM_MIN),
                deviceMemoryMaxBytes = json.optLong(KEY_DEV_MEM_MAX),
                deviceMemoryMeanBytes = json.optLong(KEY_DEV_MEM_MEAN),
            )
    }
}
