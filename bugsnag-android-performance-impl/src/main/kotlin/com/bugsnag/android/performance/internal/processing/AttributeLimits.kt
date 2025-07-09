package com.bugsnag.android.performance.internal.processing

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AttributeLimits {
    public val attributeStringValueLimit: Int
    public val attributeArrayLengthLimit: Int
    public val attributeCountLimit: Int
}
