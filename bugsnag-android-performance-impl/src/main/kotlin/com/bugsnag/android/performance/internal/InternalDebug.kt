package com.bugsnag.android.performance.internal

import android.content.Context
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

    /**
     * When in development we reduce the worker sleep time to 5 seconds, which also reduces the batch
     * timeout to 5 seconds. This results in spans being sent after 5 seconds making them appear in the
     * dashboard more quickly during development.
     */
    private const val DEVELOPMENT_WORKER_WAIT_TIME: Long = 5000L

    internal fun configure(
        inDevelopment: Boolean?,
        context: Context,
    ) {
        if ((inDevelopment ?: context.isDebuggable) == true) {
            workerSleepMs = DEVELOPMENT_WORKER_WAIT_TIME
        }
    }
}
