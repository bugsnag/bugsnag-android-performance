package com.bugsnag.android.performance.internal

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.os.SystemClock
import com.bugsnag.android.performance.Logger

internal class PerformanceActivityLifecycleCallbacks(
    private val openLoadSpans: Boolean,
    private val closeLoadSpans: Boolean,
    private val activityLoadSpans: SpanTracker<Activity>,
    private val spanFactory: SpanFactory
) : ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (openLoadSpans) {
            activityLoadSpans.track(activity) {
                spanFactory.createViewLoadSpan(
                    activity,
                    SystemClock.elapsedRealtimeNanos()
                )
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (closeLoadSpans) {
            activityLoadSpans.endSpan(activity)
        } else {
            activityLoadSpans.markSpanAutomaticEnd(activity)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activityLoadSpans.markSpanLeaked(activity)) {
            Logger.w(
                "${activity::class.java.name} appears to have leaked a ViewLoad Span. " +
                    "This is probably because BugsnagPerformance.endViewLoad was not called."
            )
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
