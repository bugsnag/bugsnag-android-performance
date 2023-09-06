package com.bugsnag.android.performance.internal

import android.os.Handler
import android.os.Looper

internal object Loopers {
    @JvmStatic
    val main = Looper.getMainLooper()

    val mainHandler = Handler(main)

    fun newMainHandler() = Handler(main)
    fun newMainHandler(callback: Handler.Callback) = Handler(main, callback)
}
