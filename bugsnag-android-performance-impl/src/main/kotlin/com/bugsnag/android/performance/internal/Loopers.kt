package com.bugsnag.android.performance.internal

import android.os.Handler
import android.os.Looper
import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public object Loopers {
    @JvmStatic
    public val main: Looper = Looper.getMainLooper()

    public val mainHandler: Handler = Handler(main)

    public fun isMainThread(): Boolean = Looper.myLooper() === main

    public fun newMainHandler(): Handler = Handler(main)

    public fun newMainHandler(callback: Handler.Callback): Handler = Handler(main, callback)

    /**
     * Run the specified code on the main thread, either immediately or posted using
     * the [mainHandler]. This is useful for running initialization code that must occur on the main
     * thread.
     */
    public inline fun onMainThread(crossinline action: () -> Unit) {
        if (isMainThread()) {
            action()
        } else {
            mainHandler.post { action() }
        }
    }
}
