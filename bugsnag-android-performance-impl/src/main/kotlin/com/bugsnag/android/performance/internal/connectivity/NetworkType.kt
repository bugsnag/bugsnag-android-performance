package com.bugsnag.android.performance.internal.connectivity

public enum class NetworkType(
    @JvmField
    public val otelName: String,
) {
    WIFI("wifi"),
    WIRED("wired"),
    CELL("cell"),
    UNAVAILABLE("unavailable"),
    UNKNOWN("unknown"),
}
