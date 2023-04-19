package com.bugsnag.android.performance.internal

enum class SpanCategory(val category: String?) {
    CUSTOM(null),
    VIEW_LOAD("view_load"),
    NETWORK("network"),
    APP_START("app_start"),
}
