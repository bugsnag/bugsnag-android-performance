package com.bugsnag.android.performance.internal

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import com.bugsnag.android.performance.Logger
import kotlin.math.max

typealias InForegroundCallback = (inForeground: Boolean) -> Unit

class PerformanceLifecycleCallbacks internal constructor(
    private val spanTracker: SpanTracker,
    private val spanFactory: SpanFactory,
    private val inForegroundCallback: InForegroundCallback,
) : ActivityLifecycleCallbacks, Handler.Callback {

    private val handler = Handler(Looper.getMainLooper(), this)

    /**
     * The number of Activities that have been created but not destroyed. This is used to determine
     * whether to treat an `Activity.onCreate` as an "app launch".
     */
    private var activityInstanceCount = 0

    /**
     * The number of Activities that have been started but not stopped. This is used to determine
     * whether the app is considered "in foreground" or not.
     */
    private var startedActivityCount = 0

    private var backgroundSent = true

    /**
     * The Span used to measure the start of the app from when the Application starts until the
     * first Activity resumes.
     */
    private var appStartSpan: SpanImpl? = null

    internal var openLoadSpans: Boolean = false

    internal var closeLoadSpans: Boolean = false

    internal var instrumentAppStart: Boolean = true

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            appStartIsForeground()
            startViewLoad(activity, savedInstanceState)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++

        if (startedActivityCount == 1 && backgroundSent) {
            inForegroundCallback(true)
        } else {
            handler.removeMessages(MSG_SEND_BACKGROUND)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            endViewLoad(activity)
        }
    }

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        appStartIsForeground()
        startViewLoad(activity, savedInstanceState)
        startViewLoadPhase(activity, ViewLoadPhase.CREATE)
    }

    override fun onActivityPreStarted(activity: Activity) {
        startViewLoadPhase(activity, ViewLoadPhase.START)
    }

    override fun onActivityPreResumed(activity: Activity) {
        startViewLoadPhase(activity, ViewLoadPhase.RESUME)
    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        endViewLoadPhase(activity, ViewLoadPhase.CREATE)
    }

    override fun onActivityPostStarted(activity: Activity) {
        endViewLoadPhase(activity, ViewLoadPhase.START)
    }

    override fun onActivityPostResumed(activity: Activity) {
        endViewLoadPhase(activity, ViewLoadPhase.RESUME)
        endViewLoad(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = max(0, startedActivityCount - 1)

        if (startedActivityCount == 0) {
            backgroundSent = false
            handler.sendEmptyMessageDelayed(MSG_SEND_BACKGROUND, BACKGROUND_TIMEOUT_MS)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        try {
            if (spanTracker.markSpanLeaked(activity)) {
                Logger.w(
                    "${activity::class.java.name} appears to have leaked a ViewLoad Span. " +
                        "This is probably because BugsnagPerformance.endViewLoad was not called.",
                )
            }
        } finally {
            // make sure we never drop below 0, this can happen if BugsnagPerformance.start was
            // called *after* the first Activity was started
            activityInstanceCount = max(0, activityInstanceCount - 1)
        }
    }

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_SEND_BACKGROUND -> {
                inForegroundCallback(false)
                backgroundSent = true
                appStartSpan = null
            }

            MSG_APP_CLASS_COMPLETE -> {
                handler.sendEmptyMessageDelayed(MSG_DISCARD_APP_START, APP_START_TIMEOUT_MS)
            }

            MSG_DISCARD_APP_START -> {
                // this means we timed out waiting for an Activity to be started
                appStartSpan = null
            }

            else -> return false
        }

        return true
    }

    fun startAppLoadSpan(startType: String) {
        if (appStartSpan == null && instrumentAppStart) {
            appStartSpan = spanFactory.createAppStartSpan(startType)
            handler.sendEmptyMessageDelayed(MSG_APP_CLASS_COMPLETE, 1)
        }
    }

    private fun maybeStartAppLoad(savedInstanceState: Bundle?) {
        if (activityInstanceCount == 0) {
            if (savedInstanceState == null) {
                startAppLoadSpan("Warm")
            } else {
                startAppLoadSpan("Hot")
            }
        }
    }

    private fun maybeEndAppStartSpan() {
        if (instrumentAppStart) {
            appStartSpan?.end()
        }

        // we may have an appStartupSpan from before the configuration was in-place
        appStartSpan = null
    }

    /**
     * Called when it becomes clear that any pending AppStart is in the foreground, so we should
     * avoid potentially discarding the AppStart span (and remove our background app-start detection).
     */
    private fun appStartIsForeground() {
        handler.removeMessages(MSG_APP_CLASS_COMPLETE)
        handler.removeMessages(MSG_DISCARD_APP_START)
    }

    private fun startViewLoad(activity: Activity, savedInstanceState: Bundle?) {
        try {
            maybeStartAppLoad(savedInstanceState)

            if (openLoadSpans) {
                spanTracker.associate(activity) {
                    spanFactory.createViewLoadSpan(activity)
                }
            }
        } finally {
            activityInstanceCount++
        }
    }

    private fun endViewLoad(activity: Activity) {
        if (closeLoadSpans) {
            spanTracker.endSpan(activity)
        } else {
            spanTracker.markSpanAutomaticEnd(activity)
        }

        maybeEndAppStartSpan()
    }

    private fun startViewLoadPhase(activity: Activity, phase: ViewLoadPhase) {
        val viewLoadSpan = spanTracker[activity]
        if (openLoadSpans && viewLoadSpan != null) {
            spanTracker.associate(activity, phase) {
                spanFactory.createViewLoadPhaseSpan(activity, phase, viewLoadSpan)
            }
        }
    }

    private fun endViewLoadPhase(activity: Activity, phase: ViewLoadPhase) {
        spanTracker.endSpan(activity, phase)
    }

    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private companion object {
        private const val MSG_SEND_BACKGROUND = 1

        /**
         * Message ID sent directly after opening an AppStart span. This begins a second timeout
         * tracking the time between the end of the `Application.onCreate` and the first
         * `Activity.onCreate` (tracked by [MSG_DISCARD_APP_START]).
         */
        private const val MSG_APP_CLASS_COMPLETE = 2

        /**
         * Message ID sent delayed (after [APP_START_TIMEOUT_MS]) to force-discard any AppStart
         * span still in-progress. This is used to avoid skewing metrics due to background starts
         * triggered by things like broadcasts, service starts or content providers being accessed.
         *
         * This message is negated by any `Activity.onCreate` which will
         * `removeMessages(MSG_DISCARD_APP_START)`.
         */
        private const val MSG_DISCARD_APP_START = 3

        /**
         * Same as `androidx.lifecycle.ProcessLifecycleOwner` and is used to avoid reporting
         * background / foreground changes when there is only 1 Activity being restarted for configuration
         * changes.
         */
        private const val BACKGROUND_TIMEOUT_MS = 700L

        /**
         * How long to wait between the Application class loading and the first Activity.onCreate before
         * discarding the AppStart span (and assuming the app-start is for a Service or Broadcast).
         */
        private const val APP_START_TIMEOUT_MS = 1000L
    }
}
