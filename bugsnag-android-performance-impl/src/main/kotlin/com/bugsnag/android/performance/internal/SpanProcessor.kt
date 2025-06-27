package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.Span

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun interface SpanProcessor {
    public fun onEnd(span: Span)
}
