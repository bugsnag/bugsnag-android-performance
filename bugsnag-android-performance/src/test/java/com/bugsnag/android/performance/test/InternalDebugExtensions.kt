package com.bugsnag.android.performance.test

import com.bugsnag.android.performance.internal.InternalDebug

/**
 * Run the given block and then restore all of the `InternalDebug` values to their previous values.
 */
inline fun <R> InternalDebug.withDebugValues(block: () -> R): R {
    val oldSpanBatchSizeSendTriggerPoint = spanBatchSizeSendTriggerPoint
    val oldSpanBatchTimeoutMs = spanBatchTimeoutMs
    val oldDropSpansOlderThanMs = dropSpansOlderThanMs
    val oldPValueExpireAfterMs = pValueExpireAfterMs

    try {
        return block()
    } finally {
        spanBatchSizeSendTriggerPoint = oldSpanBatchSizeSendTriggerPoint
        spanBatchTimeoutMs = oldSpanBatchTimeoutMs
        dropSpansOlderThanMs = oldDropSpansOlderThanMs
        pValueExpireAfterMs = oldPValueExpireAfterMs
    }
}
