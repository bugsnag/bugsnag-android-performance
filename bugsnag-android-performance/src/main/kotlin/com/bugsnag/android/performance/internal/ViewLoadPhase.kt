package com.bugsnag.android.performance.internal

enum class ViewLoadPhase(internal val spanName: String) {
    CREATE("Create"),
    START("Start"),
    RESUME("Resume"),
}



