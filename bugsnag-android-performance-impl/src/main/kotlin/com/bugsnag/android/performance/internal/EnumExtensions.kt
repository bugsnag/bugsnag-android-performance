package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.SpanKind
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

public val SpanKind.otelName: String
    get() = when (this) {
        SpanKind.INTERNAL -> "SPAN_KIND_INTERNAL"
        SpanKind.SERVER -> "SPAN_KIND_SERVER"
        SpanKind.CLIENT -> "SPAN_KIND_CLIENT"
        SpanKind.PRODUCER -> "SPAN_KIND_PRODUCER"
        SpanKind.CONSUMER -> "SPAN_KIND_CONSUMER"
    }

public val SpanKind.otelOrdinal: Int
    get() = when (this) {
        SpanKind.INTERNAL -> 1
        SpanKind.SERVER -> 2
        SpanKind.CLIENT -> 3
        SpanKind.PRODUCER -> 4
        SpanKind.CONSUMER -> 5
    }
