package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.ViewType

public val ViewType.typeName: String
    get() = when (this) {
        ViewType.ACTIVITY -> "activity"
        ViewType.FRAGMENT -> "fragment"
        ViewType.COMPOSE -> "compose"
    }

public val ViewType.spanName: String
    get() = when (this) {
        ViewType.ACTIVITY -> "Activity"
        ViewType.FRAGMENT -> "Fragment"
        ViewType.COMPOSE -> "Compose"
    }
