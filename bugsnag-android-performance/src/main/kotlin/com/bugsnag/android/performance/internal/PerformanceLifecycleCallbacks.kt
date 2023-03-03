package com.bugsnag.android.performance.internal

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import com.bugsnag.android.performance.Logger
import kotlin.math.max

typealias InForegroundCallback = (inForeground: Boolean) -> Unit

private const val MSG_SEND_BACKGROUND = 1

/**
 * Same as `androidx.lifecycle.ProcessLifecycleOwner` and is used to avoid reporting
 * background / foreground changes when there is only 1 Activity being restarted for configuration
 * changes.
 */
private const val BACKGROUND_TIMEOUT_MS = 700L

class PerformanceLifecycleCallbacks internal constructor(
    private val spanTracker: SpanTracker,
    private val spanFactory: SpanFactory,
    private val inForegroundCallback: InForegroundCallback
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
    private var appStartupSpan: SpanImpl? = null

    internal var openLoadSpans: Boolean = false

    internal var closeLoadSpans: Boolean = false

    internal var instrumentAppStart: Boolean = true

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
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

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++

        if (startedActivityCount == 1 && backgroundSent) {
            inForegroundCallback(true)
        } else {
            handler.removeMessages(MSG_SEND_BACKGROUND)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        maybeEndAppLoad(activity)

        // we may have an appStartupSpan from before the configuration was in-place
        appStartupSpan = null

        if (closeLoadSpans) {
            spanTracker.endSpan(activity)
        } else {
            spanTracker.markSpanAutomaticEnd(activity)
        }
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
                        "This is probably because BugsnagPerformance.endViewLoad was not called."
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
                appStartupSpan = null
            }

            else -> return false
        }

        return true
    }

    fun startAppLoadSpan(startType: String) {
        if (appStartupSpan == null && instrumentAppStart) {
            appStartupSpan = spanFactory.createAppStartSpan(startType)
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

    private fun maybeEndAppLoad(activity: Activity?) {
        if (instrumentAppStart) {
            appStartupSpan?.apply {
                if (activity != null) {
                    val activityName = activity::class.java.simpleName
                    setAttribute("bugsnag.view.type", "Activity")
                    setAttribute("bugsnag.first_view", activityName)
                }
            }

            appStartupSpan?.end()
        }
    }

    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
