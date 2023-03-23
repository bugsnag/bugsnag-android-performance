package com.bugsnag.android.performance.internal

enum class ViewLifecyclePhase(internal val spanName: String) {
    NONE(""),
    CREATE("Create"),
    START("Start"),
    RESUME("Resume"),
}



