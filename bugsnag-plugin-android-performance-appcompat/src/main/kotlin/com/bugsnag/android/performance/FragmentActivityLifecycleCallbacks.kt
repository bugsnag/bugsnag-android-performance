package com.bugsnag.android.performance

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanTracker

class FragmentActivityLifecycleCallbacks(
    private val spanTracker: SpanTracker,
) : ActivityLifecycleCallbacks, FragmentLifecycleCallbacks() {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is FragmentActivity) return

        activity.supportFragmentManager
            .registerFragmentLifecycleCallbacks(this, true)
    }

    override fun onFragmentPreCreated(
        fm: FragmentManager,
        f: Fragment,
        savedInstanceState: Bundle?,
    ) {
        spanTracker.associate(f) { BugsnagPerformance.startViewLoadSpan(f) as SpanImpl }
    }

    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
        spanTracker.endSpan(f)
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
