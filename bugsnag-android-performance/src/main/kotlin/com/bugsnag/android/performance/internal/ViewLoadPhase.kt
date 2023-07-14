package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.ViewType

enum class ViewLoadPhase(private val phaseName: String) {
    CREATE("Create"),
    START("Start"),
    RESUME("Resume");

    internal fun phaseNameFor(viewType: ViewType): String {
        return viewType.spanName + phaseName
    }
}
