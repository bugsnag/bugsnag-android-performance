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

    public var procIoPath: String = "/proc/self/io"

    /**
     * When true, successful disk IOPS collection also attaches the start/end syscall
     * snapshots used in the formula. Maze uses these to assert end >= start.
     * Do not enable in production.
     */
    public var attachDiskIoSnapshots: Boolean = false

    /**
     * Maze-only: force DiskIoMetricsSource to treat span duration as invalid.
     * `"zero"` → end timestamp equals start; `"negative"` → end is before start.
     * Empty means use the real clock. Do not enable in production.
     */

    public var diskIoTimestampFault: String = ""

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
