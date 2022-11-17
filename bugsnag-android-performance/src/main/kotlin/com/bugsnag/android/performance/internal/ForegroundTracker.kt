package com.bugsnag.android.performance.internal

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo

private const val IMPORTANCE_FOREGROUND_SERVICE = 125

internal object ForegroundTracker {
    /**
     * Determines whether or not the application is in the foreground, by using the process'
     * importance as a proxy.
     *
     * In the unlikely event that information about the process cannot be retrieved, this method
     * will return null, and the 'bugsnag.app.in_foreground' attribute will not be added to
     * new `Span`s.
     *
     * @return whether the application is in the foreground or not
     */
    val isInForeground: Boolean?
        get() = try {
            processInfo.importance <= IMPORTANCE_FOREGROUND_SERVICE
        } catch (exc: RuntimeException) {
            null
        }

    private val processInfo: RunningAppProcessInfo
        get() {
            val info = RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(info)
            return info
        }
}
