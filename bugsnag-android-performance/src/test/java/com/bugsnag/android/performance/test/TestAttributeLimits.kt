package com.bugsnag.android.performance.test

import com.bugsnag.android.performance.internal.processing.AttributeLimits

data class TestAttributeLimits(
    override val attributeStringValueLimit: Int = 128,
    override val attributeArrayLengthLimit: Int = 1000,
    override val attributeCountLimit: Int = 100,
) : AttributeLimits
