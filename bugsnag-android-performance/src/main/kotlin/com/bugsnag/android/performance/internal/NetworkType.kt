package com.bugsnag.android.performance.internal

enum class NetworkType(
    @JvmField
    internal val otelName: String
) {
    WIFI("wifi"),
    WIRED("wired"),
    CELL("cell"),
    UNAVAILABLE("unavailable"),
    UNKNOWN("unknown"),
}
