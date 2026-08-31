package com.bugsnag.android.performance.internal.metrics

import androidx.annotation.RestrictTo

/**
 * Captures a single disk I/O snapshot at a point in time.
 *
 * @property readSyscalls Total cumulative read syscalls (syscr from /proc/self/io).
 * @property writeSyscalls Total cumulative write syscalls (syscw from /proc/self/io).
 * @property timestampNanos Timestamp when this snapshot was captured, in nanoseconds (elapsedRealtimeNanos).
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public data class DiskIoSnapshot(
    val readSyscalls: Long,
    val writeSyscalls: Long,
    val timestampNanos: Long,
)

