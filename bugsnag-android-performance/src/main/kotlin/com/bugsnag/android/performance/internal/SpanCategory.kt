package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum class SpanCategory(public val category: String) {
    CUSTOM("custom"),
    VIEW_LOAD("view_load"),
    VIEW_LOAD_PHASE("view_load_phase"),
    NETWORK("network"),
    APP_START("app_start"),
    APP_START_PHASE("app_start_phase"),
}
