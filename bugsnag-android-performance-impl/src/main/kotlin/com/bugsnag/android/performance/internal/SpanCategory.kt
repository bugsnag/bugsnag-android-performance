package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo

@JvmInline
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public value class SpanCategory internal constructor(public val category: String) {
    public companion object {
        public val CUSTOM: SpanCategory = SpanCategory("custom")
        public val VIEW_LOAD: SpanCategory = SpanCategory("view_load")
        public val VIEW_LOAD_PHASE: SpanCategory = SpanCategory("view_load_phase")
        public val NETWORK: SpanCategory = SpanCategory("network")
        public val APP_START: SpanCategory = SpanCategory("app_start")
        public val APP_START_PHASE: SpanCategory = SpanCategory("app_start_phase")
    }
}
