package com.bugsnag.android.performance.internal

import android.os.Handler
import android.os.Looper

internal object Loopers {
    @JvmStatic
    val main: Looper = Looper.getMainLooper()

    val mainHandler = Handler(main)

    fun isMainThread(): Boolean = Looper.myLooper() === main

    fun newMainHandler() = Handler(main)
    fun newMainHandler(callback: Handler.Callback) = Handler(main, callback)

    /**
     * Run the specified code on the main thread, either immediately or posted using
     * the [mainHandler]. This is useful for running initialization code that must occur on the main
     * thread.
     */
    inline fun onMainThread(crossinline action: () -> Unit) {
        if (isMainThread()) {
            action()
        } else {
            mainHandler.post { action() }
        }
    }
}
