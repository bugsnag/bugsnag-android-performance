package com.bugsnag.android.performance.internal

/**
 * Warning: Everything in here is to support internal testing, and is subject to change
 * without notice.
 * DO NOT USE!
 */
object InternalDebug {
    var spanBatchSizeSendTriggerPoint = 100
    var spanBatchTimeoutMs = 30000L
    var dropSpansOlderThanMs = 24L * 60 * 60 * 1000
}
