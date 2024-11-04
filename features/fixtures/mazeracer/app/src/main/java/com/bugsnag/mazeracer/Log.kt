package com.bugsnag.mazeracer

import android.util.Log
import com.bugsnag.android.Logger

fun log(msg: String) {
    Log.d("BugsnagMazeRacer", msg)
}

fun log(
    msg: String,
    e: Exception,
) {
    Log.e("BugsnagMazeRacer", msg, e)
}

object BugsnagLogger : Logger {
    private const val TAG = "Bugsnag"

    override fun d(msg: String) {
        Log.d(TAG, msg)
    }

    override fun d(
        msg: String,
        throwable: Throwable,
    ) {
        Log.d(TAG, msg, throwable)
    }

    override fun i(msg: String) {
        Log.i(TAG, msg)
    }

    override fun i(
        msg: String,
        throwable: Throwable,
    ) {
        Log.i(TAG, msg, throwable)
    }

    override fun w(msg: String) {
        Log.w(TAG, msg)
    }

    override fun w(
        msg: String,
        throwable: Throwable,
    ) {
        Log.w(TAG, msg, throwable)
    }

    override fun e(msg: String) {
        Log.e(TAG, msg)
    }

    override fun e(
        msg: String,
        throwable: Throwable,
    ) {
        Log.e(TAG, msg, throwable)
    }
}
