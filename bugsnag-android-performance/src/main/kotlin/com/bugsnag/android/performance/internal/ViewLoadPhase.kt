package com.bugsnag.android.performance.internal

enum class ViewLoadPhase(internal val phaseName: String) {
    CREATE("ActivityCreate"),
    START("ActivityStart"),
    RESUME("ActivityResume"),
}



