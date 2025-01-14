package com.bugsnag.android.performance.internal.instrumentation

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.bugsnag.android.performance.internal.AutoInstrumentationCache
import com.bugsnag.android.performance.internal.ViewLoadPhase

internal abstract class AbstractActivityCallbacks(
    protected val startInstrumentation: AppStartInstrumentation,
    protected val activityInstrumentation: ActivityInstrumentation,
    protected val autoInstrumentationCache: AutoInstrumentationCache,
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityStarted(activity: Activity) {
        activityInstrumentation.cancelDiscardViewLoads()
    }

    override fun onActivityStopped(activity: Activity) {
        activityInstrumentation.postDiscardViewLoad(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

internal class LegacyActivityInstrumentation(
    startInstrumentation: AppStartInstrumentation,
    activityInstrumentation: ActivityInstrumentation,
    autoInstrumentationCache: AutoInstrumentationCache,
) : AbstractActivityCallbacks(
    startInstrumentation,
    activityInstrumentation,
    autoInstrumentationCache,
) {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        startInstrumentation.onActivityCreate(savedInstanceState != null)
        activityInstrumentation.autoStartViewLoadSpan(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        activityInstrumentation.autoEndViewLoadSpan(activity)
    }
}

internal class ActivityCallbacks(
    startInstrumentation: AppStartInstrumentation,
    activityInstrumentation: ActivityInstrumentation,
    autoInstrumentationCache: AutoInstrumentationCache,
) : AbstractActivityCallbacks(
    startInstrumentation,
    activityInstrumentation,
    autoInstrumentationCache,
) {

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        startInstrumentation.onActivityCreate(savedInstanceState != null)
        activityInstrumentation.autoStartViewLoadSpan(activity)
        activityInstrumentation.startViewLoadPhase(activity, ViewLoadPhase.CREATE)
    }

    override fun onActivityPreStarted(activity: Activity) {
        activityInstrumentation.endViewLoadPhase(activity, ViewLoadPhase.CREATE)
        activityInstrumentation.startViewLoadPhase(activity, ViewLoadPhase.START)
    }

    override fun onActivityPreResumed(activity: Activity) {
        activityInstrumentation.endViewLoadPhase(activity, ViewLoadPhase.START)
        activityInstrumentation.startViewLoadPhase(activity, ViewLoadPhase.RESUME)
    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        activityInstrumentation.endViewLoadPhase(activity, ViewLoadPhase.CREATE)
    }

    override fun onActivityPostStarted(activity: Activity) {
        activityInstrumentation.endViewLoadPhase(activity, ViewLoadPhase.START)
    }

    override fun onActivityPostResumed(activity: Activity) {
        activityInstrumentation.endViewLoadPhase(activity, ViewLoadPhase.RESUME)
        activityInstrumentation.autoEndViewLoadSpan(activity)
    }
}
