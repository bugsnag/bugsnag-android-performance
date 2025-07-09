package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo

@JvmInline
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public value class SpanCategory internal constructor(public val category: String) {
    public companion object {
        public const val CATEGORY_CUSTOM: String = "custom"
        public const val CATEGORY_VIEW_LOAD: String = "view_load"
        public const val CATEGORY_VIEW_LOAD_PHASE: String = "view_load_phase"
        public const val CATEGORY_NETWORK: String = "network"
        public const val CATEGORY_APP_START: String = "app_start"
        public const val CATEGORY_APP_START_PHASE: String = "app_start_phase"

        public val CUSTOM: SpanCategory = SpanCategory(CATEGORY_CUSTOM)
        public val VIEW_LOAD: SpanCategory = SpanCategory(CATEGORY_VIEW_LOAD)
        public val VIEW_LOAD_PHASE: SpanCategory = SpanCategory(CATEGORY_VIEW_LOAD_PHASE)
        public val NETWORK: SpanCategory = SpanCategory(CATEGORY_NETWORK)
        public val APP_START: SpanCategory = SpanCategory(CATEGORY_APP_START)
        public val APP_START_PHASE: SpanCategory = SpanCategory(CATEGORY_APP_START_PHASE)
    }
}
