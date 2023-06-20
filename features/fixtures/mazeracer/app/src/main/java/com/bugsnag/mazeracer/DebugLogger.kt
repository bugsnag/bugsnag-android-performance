package com.bugsnag.mazeracer

import android.util.Log
import com.bugsnag.android.performance.Logger

object DebugLogger : Logger {

    private const val TAG = "Bugsnag"

    override fun e(msg: String) {
        Log.e(TAG, msg)
    }

    override fun e(msg: String, throwable: Throwable) {
        Log.e(TAG, msg, throwable)
    }

    override fun w(msg: String) {
        Log.w(TAG, msg)
    }

    override fun w(msg: String, throwable: Throwable) {
        Log.w(TAG, msg, throwable)
    }

    override fun i(msg: String) {
        Log.i(TAG, msg)
    }

    override fun i(msg: String, throwable: Throwable) {
        Log.i(TAG, msg, throwable)
    }

    override fun d(msg: String) {
        Log.d(TAG, msg)
    }

    override fun d(msg: String, throwable: Throwable) {
        Log.d(TAG, msg, throwable)
    }
}
