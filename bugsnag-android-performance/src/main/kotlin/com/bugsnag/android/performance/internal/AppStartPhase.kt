package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum class AppStartPhase(internal val phaseName: String) {
    FRAMEWORK("Framework"),
}
