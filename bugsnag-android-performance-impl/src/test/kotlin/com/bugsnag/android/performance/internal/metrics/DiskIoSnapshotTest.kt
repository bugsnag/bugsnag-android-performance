package com.bugsnag.android.performance.internal.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

internal class DiskIoSnapshotTest {
    @Test
    fun dataClassHoldsValues() {
        val snapshot =
            DiskIoSnapshot(
                readSyscalls = 100L,
                writeSyscalls = 50L,
                timestampNanos = 999L,
            )

        assertEquals(100L, snapshot.readSyscalls)
        assertEquals(50L, snapshot.writeSyscalls)
        assertEquals(999L, snapshot.timestampNanos)
    }

    @Test
    fun dataClassEquality() {
        val first =
            DiskIoSnapshot(
                readSyscalls = 100L,
                writeSyscalls = 50L,
                timestampNanos = 999L,
            )
        val second =
            DiskIoSnapshot(
                readSyscalls = 100L,
                writeSyscalls = 50L,
                timestampNanos = 999L,
            )
        val different =
            DiskIoSnapshot(
                readSyscalls = 101L,
                writeSyscalls = 50L,
                timestampNanos = 999L,
            )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, different)
    }
}
