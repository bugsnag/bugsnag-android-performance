package com.bugsnag.android.performance.internal

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.os.SystemClock
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.Span
import kotlin.math.max

internal class PerformancePlatformCallbacks(
    private val activityLoadSpans: SpanTracker<Activity>,
    private val spanFactory: SpanFactory
) : ActivityLifecycleCallbacks {

    /**
     * The number of Activities that have been created but not destroyed. This is used to determine
     * whether to treat an `Activity.onCreate` as an "app launch".
     */
    private var activeActivityCount = 0

    /**
     * The Span used to measure the start of the app from when the Application starts until the
     * first Activity resumes.
     */
    private var appStartupSpan: Span? = null

    internal var openLoadSpans: Boolean = false

    internal var closeLoadSpans: Boolean = false

    internal var instrumentAppStart: Boolean = true

    fun startAppLoadSpanUnderLock(startType: String) {
        if (appStartupSpan == null && instrumentAppStart) {
            appStartupSpan = spanFactory.createAppStartSpan(startType)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        try {
            if (activeActivityCount == 0) {
                if (savedInstanceState == null) {
                    startAppLoadSpanUnderLock("Warm")
                } else {
                    startAppLoadSpanUnderLock("Hot")
                }
            }

            if (openLoadSpans) {
                activityLoadSpans.track(activity) {
                    spanFactory.createViewLoadSpan(activity, SystemClock.elapsedRealtimeNanos())
                }
            }
        } finally {
            activeActivityCount++
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (instrumentAppStart) {
            appStartupSpan?.end()
        }

        // we may have an appStartupSpan from before the configuration was in-place
        appStartupSpan = null

        if (closeLoadSpans) {
            activityLoadSpans.endSpan(activity)
        } else {
            activityLoadSpans.markSpanAutomaticEnd(activity)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        try {
            if (activityLoadSpans.markSpanLeaked(activity)) {
                Logger.w(
                    "${activity::class.java.name} appears to have leaked a ViewLoad Span. " +
                        "This is probably because BugsnagPerformance.endViewLoad was not called."
                )
            }
        } finally {
            // make sure we never drop below 0 (somehow)
            activeActivityCount = max(0, activeActivityCount - 1)
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
