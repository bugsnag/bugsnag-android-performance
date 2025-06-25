package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo

/**
 * Warning: Everything in here is to support internal testing, and is subject to change
 * without notice.
 * DO NOT USE!
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public object InternalDebug {
    public var spanBatchSizeSendTriggerPoint: Int = 100

    /**
     * The maximum amount of time the worker thread will sleep without a `wake()`
     */
    public var workerSleepMs: Long = 30000L

    public var dropSpansOlderThanMs: Long = 24L * 60 * 60 * 1000
    public var pValueExpireAfterMs: Int = 24 * 60 * 60 * 1000
}
