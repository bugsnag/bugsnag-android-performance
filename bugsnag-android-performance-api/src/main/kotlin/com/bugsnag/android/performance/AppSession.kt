package com.bugsnag.android.performance

import java.util.UUID

/**
 * Represents a logical app session — from the moment the user brings the app to foreground
 * until they leave or the session is explicitly ended.
 *
 * Sessions are controlled via [PerformanceConfiguration.appSessionConfig] and the manual APIs:
 * [BugsnagPerformance.startAppSessionSpan] and [BugsnagPerformance.endAppSessionSpan].
 */
public class AppSession internal constructor(
    /** Stable UUID that identifies this session across all delivered batches. */
    public val sessionId: String = UUID.randomUUID().toString(),
    /** Wall-clock start time in nanoseconds (System.nanoTime() epoch). */
    public val startTimeNano: Long,
    /** App version string at the time the session started. */
    public val appVersion: String,
    /** Android OS version (e.g. "15"). */
    public val osVersion: String,
    /** Device model name (e.g. "Pixel 8"). */
    public val deviceModel: String,
) {
    /** Wall-clock end time in nanoseconds; null while the session is still open. */
    public var endTimeNano: Long? = null
        internal set

    /** Reason the session was closed; null while the session is still open. */
    public var closeReason: CloseReason? = null
        internal set

    /** Monotonically increasing batch index; incremented each time a batch is sent. */
    public var batchIndex: Int = 0
        internal set

    /** Whether the app is currently in the foreground during this session. */
    public var isInForeground: Boolean = true
        internal set

    /** True once [endTimeNano] has been set. */
    public val isEnded: Boolean get() = endTimeNano != null

    override fun toString(): String =
        "AppSession(sessionId=$sessionId, appVersion=$appVersion, " +
            "closeReason=$closeReason, isEnded=$isEnded)"
}

/**
 * The reason an [AppSession] was ended.
 */
public enum class CloseReason {
    /** The client app explicitly ended an app-session span. */
    CLIENT_END,

    /** The app stayed in the background longer than [AppSessionConfig.backgroundTimeoutMs]. */
    BACKGROUND_TIMEOUT,

    /** The SDK detected an out-of-memory condition and had to terminate the session early. */
    OOM,

    /** The session was recovered from a previous process that died unexpectedly. */
    PROCESS_DEATH,

    /** The operating system killed the app process. */
    APP_KILLED,

    /** The server-side TTL expired before the final batch was received. */
    TTL_EXPIRED,
}
