package com.bugsnag.mazeracer

import android.util.Log

fun log(msg: String) {
    Log.d("BugsnagMazeRacer", msg)
}

fun log(msg: String, e: Exception) {
    Log.e("BugsnagMazeRacer", msg, e)
}
