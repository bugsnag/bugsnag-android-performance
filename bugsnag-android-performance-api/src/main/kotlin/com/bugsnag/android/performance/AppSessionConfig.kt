package com.bugsnag.android.performance

/**
 * Configuration options that control how app sessions are created, maintained, and finalized.
 *
 * Add to `PerformanceConfiguration` before calling SDK `start(...)`:
 *
 * ```kotlin
 * BugsnagPerformance.start(
 *     PerformanceConfiguration.load(this).apply {
 *         appSessionConfig = AppSessionConfig(
 *             autoStartSession = true,
 *             backgroundTimeoutMs = 30_000L,
 *             sessionCallbacks = listOf(mySessionCallback)
 *         )
 *     }
 * )
 * ```
 */
public class AppSessionConfig(
    /**
      * When true the SDK automatically starts a new session when the app comes to the foreground
      * and ends it when the app leaves the foreground and the [backgroundTimeoutMs] elapses.
      *
      * When false, callers manually control segments via
      * [BugsnagPerformance.startAppSessionSpan] and [BugsnagPerformance.endAppSessionSpan].
      *
      * Default: **true**
      */
    public var autoStartSession: Boolean = true,

    /**
      * How long (in milliseconds) the app must stay in the background before the current session
      * is automatically finalised with [CloseReason.BACKGROUND_TIMEOUT].
      *
      * If the user returns to the app before this timeout fires the existing session continues.
      *
      * Default: **30 000 ms (30 seconds)**
      */
    public var backgroundTimeoutMs: Long = DEFAULT_BACKGROUND_TIMEOUT_MS,

    /**
      * Maximum duration (in milliseconds) a single session may remain open before being
      * automatically finalised. Use 0 to disable the cap.
      *
      * Default: **0 (no cap)**
      */
    public var maxSessionDurationMs: Long = 0L,

    /**
      * Optional default custom app-session name used by manual
      * [BugsnagPerformance.startAppSessionSpan] calls when no `appSessionName` is provided.
      *
      * This setting is ignored if `appSessionName` is explicitly provided to the start call.
      *
      * Default: **null**
      */
    public var manualSessionDefaultName: String? = null,

    /**
      * Callbacks invoked on session lifecycle events (started / backgrounded /
      * foregrounded / ended).  Callbacks are called on the main thread.
      */
    public var sessionCallbacks: List<AppSessionCallback> = emptyList(),
) {
    public companion object {
        /** Default background grace-period before a session is closed: 30 s. */
        public const val DEFAULT_BACKGROUND_TIMEOUT_MS: Long = 30_000L
    }
}

/**
 * Callback interface for observing [AppSession] lifecycle transitions.
 */
public interface AppSessionCallback {
    /** Called immediately after a new session is created and started. */
    public fun onSessionStarted(session: AppSession) {}

    /** Called when the app moves to the background (session still open). */
    public fun onSessionBackgrounded(session: AppSession) {}

    /** Called when the app returns to the foreground within the timeout window. */
    public fun onSessionForegrounded(session: AppSession) {}

    /** Called after the session has been finalised and the last batch has been queued. */
    public fun onSessionEnded(session: AppSession) {}
}

