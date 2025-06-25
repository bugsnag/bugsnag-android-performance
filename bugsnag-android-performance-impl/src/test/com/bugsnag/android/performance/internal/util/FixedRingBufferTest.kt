package com.bugsnag.android.performance.internal.util

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FixedRingBufferTest {
    @Test
    fun cannotIterateEmpty() {
        val fixedRingBuffer = FixedRingBuffer(64, ::Content)
        fixedRingBuffer.forEach(0, 10) {
            fail("empty buffers should not call forEach")
        }

        fixedRingBuffer.forEach(96, 128) {
            fail("empty buffers should not call forEach")
        }
    }

    @Test
    fun iteratesOnUndersized() {
        val fixedRingBuffer = FixedRingBuffer(64, ::Content)
        repeat(4) { index -> fixedRingBuffer.put { it.value = index } }
        val start = fixedRingBuffer.currentIndex
        repeat(4) { index -> fixedRingBuffer.put { it.value = index + 100 } }
        val end = fixedRingBuffer.currentIndex
        repeat(10) { index -> fixedRingBuffer.put { it.value = index + 1000 } }

        var expectedValue = 100
        fixedRingBuffer.forEach(start, end) { item ->
            assertEquals(expectedValue, item.value)
            expectedValue++
        }

        assertEquals(104, expectedValue)
    }

    @Test
    fun iteratesOnOversize() {
        val fixedRingBuffer = FixedRingBuffer(64, ::Content)
        repeat(128) { index -> fixedRingBuffer.put { it.value = index } }
        val start = fixedRingBuffer.currentIndex
        repeat(16) { index -> fixedRingBuffer.put { it.value = index + 100 } }
        val end = fixedRingBuffer.currentIndex
        repeat(10) { index -> fixedRingBuffer.put { it.value = index + 1000 } }

        var expectedValue = 100
        fixedRingBuffer.forEach(start, end) { item ->
            assertEquals(expectedValue, item.value)
            expectedValue++
        }

        assertEquals(116, expectedValue)
    }

    @Test
    fun iteratesFullBufferOnOversize() {
        val fixedRingBuffer = FixedRingBuffer(64, ::Content)
        repeat(128) { index -> fixedRingBuffer.put { it.value = index } }
        val start = fixedRingBuffer.currentIndex
        repeat(64) { index -> fixedRingBuffer.put { it.value = index + 100 } }
        val end = fixedRingBuffer.currentIndex
        repeat(10) { index -> fixedRingBuffer.put { it.value = index + 1000 } }

        // we've added 10 more items to the buffer, so we can only iterate over 54 of our expected
        // values now
        var expectedValue = 110
        fixedRingBuffer.forEach(start, end) { item ->
            assertEquals(expectedValue, item.value)
            expectedValue++
        }

        assertEquals(164, expectedValue)
    }

    @Test
    fun cannotIterateOutdated() {
        val fixedRingBuffer = FixedRingBuffer(64, ::Content)
        repeat(8) { index -> fixedRingBuffer.put { it.value = index } }
        val start = fixedRingBuffer.currentIndex
        repeat(10) { index -> fixedRingBuffer.put { it.value = index + 1000 } }
        val end = fixedRingBuffer.currentIndex
        repeat(256) { index -> fixedRingBuffer.put { it.value = index + 100 } }

        fixedRingBuffer.forEach(start, end) {
            fail("forEach should not work for samples too far in the past")
        }
    }

    data class Content(
        var value: Int = 0,
    )
}
