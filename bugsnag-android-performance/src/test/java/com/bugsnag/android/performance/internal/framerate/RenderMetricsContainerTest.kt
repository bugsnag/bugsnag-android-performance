package com.bugsnag.android.performance.internal.framerate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderMetricsContainerTest {
    @Test
    fun countFrozenFrames() {
        val container = RenderMetricsContainer()
        // add some frames before the start snapshot
        container.update { 1.also { container.countFrozenFrame(1L, 2L) } }
        container.update { 1.also { container.countFrozenFrame(3L, 4L) } }

        val snapshot1 = container.snapshot()
        for (i in 10L..500L step 2) {
            container.update { 1.also { container.countFrozenFrame(i, i + 1) } }
        }
        val snapshot2 = container.snapshot()

        // add some extra frames after the end snapshot
        container.update { 1.also { container.countFrozenFrame(1000L, 1001L) } }
        container.update { 1.also { container.countFrozenFrame(1002L, 1003L) } }

        val timestamps = frozenFramePairs(snapshot1, snapshot2)

        assertFalse(timestamps.contains(1L to 2L))
        assertFalse(timestamps.contains(3L to 4L))
        assertFalse(timestamps.contains(1000L to 1001L))
        assertFalse(timestamps.contains(1002L to 1003L))

        for (i in 10L..500L step 2) {
            assertTrue(
                "should contain $i->${i + 1}",
                timestamps.contains(i to i + 1),
            )
        }
    }

    @Test
    fun noFrozenFrames() {
        val container = RenderMetricsContainer()
        container.update { 1.also { container.countFrozenFrame(1L, 2L) } }
        container.update { 1.also { container.countFrozenFrame(3L, 4L) } }

        val snapshot1 = container.snapshot()
        val snapshot2 = container.snapshot()
        val timestamps = frozenFramePairs(snapshot1, snapshot2)

        assertEquals(0, timestamps.size)
    }

    private fun frozenFramePairs(
        snapshot1: RenderMetricsSnapshot,
        snapshot2: RenderMetricsSnapshot,
    ): Set<Pair<Long, Long>> {
        val timestamps = HashSet<Pair<Long, Long>>()
        snapshot1.forEachFrozenFrameUntil(snapshot2) { start, end ->
            timestamps.add(start to end)
        }
        return timestamps
    }
}
