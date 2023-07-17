package com.bugsnag.android.performance

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.os.SystemClock
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.SpanTracker
import com.bugsnag.android.performance.internal.ViewLoadPhase

class FragmentActivityLifecycleCallbacks(
    private val spanTracker: SpanTracker,
    private val spanFactory: SpanFactory,
) : ActivityLifecycleCallbacks, FragmentLifecycleCallbacks() {

    /**
     * ViewLoad for Fragments is not a context Span because Fragment lifecycles can be interleaved:
     * - Fragment1 PreCreate
     * - Fragment1 Create
     * - Fragment2 PreCreate
     * - Fragment2 Create
     * - Fragment1 Started
     * - Fragment2 Started
     * - Fragment1 Resumed
     * - Fragment2 Resumed
     *
     * As such we don't want Fragment2 spans to nest under the Fragment1 ViewLoad. Instead we
     * measure ViewLoad from PreCreate -> Resume, and use a ViewLoadPhase/FragmentCreate
     * as the context for [Fragment.onCreate] which does not interleave with other fragments.
     */
    private val viewLoadOpts = SpanOptions.DEFAULTS.makeCurrentContext(false)

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
        // we start both ViewLoad & ViewLoadPhase/Create at exactly the same timestamp
        val timestamp = SystemClock.elapsedRealtimeNanos()
        val viewLoad = spanTracker.associate(f) {
            spanFactory.createViewLoadSpan(
                ViewType.FRAGMENT,
                viewNameForFragment(f),
                viewLoadOpts.startTime(timestamp),
            )
        }

        spanTracker.associate(f, ViewLoadPhase.CREATE) {
            spanFactory.createViewLoadPhaseSpan(
                viewNameForFragment(f),
                ViewType.FRAGMENT,
                ViewLoadPhase.CREATE,
                SpanOptions.DEFAULTS
                    .within(viewLoad)
                    .startTime(timestamp),
            )
        }
    }

    override fun onFragmentCreated(fm: FragmentManager, f: Fragment, savedInstanceState: Bundle?) {
        if (!f.isAdded) {
            // remove & discard the Fragment span
            spanTracker.removeAssociation(f, ViewLoadPhase.CREATE)?.discard()
            spanTracker.removeAssociation(f)?.discard()
        } else {
            spanTracker.endSpan(f, ViewLoadPhase.CREATE)
        }
    }

    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
        spanTracker.endAllSpans(f)
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
