package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.ViewType

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum class ViewLoadPhase(private val phaseName: String) {
    CREATE("Create"),
    START("Start"),
    RESUME("Resume"),
    ;

    internal fun phaseNameFor(viewType: ViewType): String {
        return viewType.spanName + phaseName
    }
}
