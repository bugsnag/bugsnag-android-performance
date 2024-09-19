package com.bugsnag.android.performance.internal.processing

internal interface AttributeLimits {
    val attributeStringValueLimit: Int
    val attributeArrayLengthLimit: Int
    val attributeCountLimit: Int
}
