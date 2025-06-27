package com.bugsnag.android.performance.internal.connectivity

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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
