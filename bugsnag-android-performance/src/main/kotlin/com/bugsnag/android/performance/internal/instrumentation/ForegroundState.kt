package com.bugsnag.android.performance.internal.instrumentation

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import com.bugsnag.android.performance.internal.Loopers
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.max

internal typealias InForegroundCallback = (inForeground: Boolean) -> Unit

internal object ForegroundState : ActivityLifecycleCallbacks {

    /**
     * Same as `androidx.lifecycle.ProcessLifecycleOwner` and is used to avoid reporting
     * background / foreground changes when there is only 1 Activity being restarted for configuration
     * changes.
     */
    private const val BACKGROUND_TIMEOUT_MS = 700L

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

    private val listeners = CopyOnWriteArraySet<InForegroundCallback>()

    private val sendInBackground: Runnable = Runnable {
        if (!backgroundSent) {
            isInForeground = false
            backgroundSent = true
        }
    }

    var isInForeground: Boolean = false
        @VisibleForTesting
        internal set(value) {
            if (value != field) {
                field = value
                dispatchForegroundChangedCallback(value)
            }
        }

    fun addForegroundChangedCallback(inForegroundCallback: InForegroundCallback) {
        listeners.add(inForegroundCallback)
    }

    fun removeForegroundChangedCallback(inForegroundCallback: InForegroundCallback) {
        listeners.remove(inForegroundCallback)
    }

    private fun dispatchForegroundChangedCallback(inForeground: Boolean) {
        listeners.forEach { it(inForeground) }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activityInstanceCount++
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++
        Loopers.mainHandler.removeCallbacks(sendInBackground)
        isInForeground = true
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = max(0, startedActivityCount - 1)

        if (startedActivityCount == 0) {
            backgroundSent = false
            Loopers.mainHandler.postDelayed(sendInBackground, BACKGROUND_TIMEOUT_MS)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        activityInstanceCount = max(0, activityInstanceCount - 1)
    }

    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

}
