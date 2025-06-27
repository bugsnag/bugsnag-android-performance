package com.bugsnag.android.performance.internal

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
import android.app.Application
import android.os.Build
import androidx.annotation.RestrictTo

/**
 * Attempts to determine whether the app is visible, optionally using a provided `Application` class.
 * This should be considered an estimate and is not always completely accurate, but serves as a base
 * before `BugsnagPerformance.start` has been called, and before `PerformanceLifecycleCallbacks`
 * have started tracking `Activity` creation.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun isInForeground(application: Application? = null): Boolean? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && application != null) {
        try {
            application.getActivityManager()?.appTasks?.any { it.taskInfo.numActivities > 0 } == true
        } catch (exc: RuntimeException) {
            null
        }
    } else {
        try {
            processInfo.importance <= IMPORTANCE_FOREGROUND_SERVICE
        } catch (exc: RuntimeException) {
            null
        }
    }
}

private val processInfo: ActivityManager.RunningAppProcessInfo
    get() {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info
    }
