package com.bugsnag.android.performance.internal

enum class SpanCategory(val category: String?) {
    CUSTOM(null),
    VIEW_LOAD("view_load"),
    VIEW_LOAD_PHASE("view_load_phase"),
    NETWORK("network"),
    APP_START("app_start"),
    APP_START_PHASE("app_start_phase"),
}
