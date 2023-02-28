package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.SpanContext

/**
 * Allows getting/setting the Span Context stack for the current thread.
 *
 * This is utilized by the BugSnag Performance Coroutines plugin and is intended for
 * internal use only.
 */
@get:JvmSynthetic
@set:JvmSynthetic
var SpanContext.Storage.currentStack
    get() = contextStack
    set(value) = setContextStackUnsafe(value)
