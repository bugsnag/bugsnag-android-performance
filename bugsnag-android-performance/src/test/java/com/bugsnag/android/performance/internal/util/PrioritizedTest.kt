package com.bugsnag.android.performance.internal.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PrioritizedTest {
    @Test
    fun testNaturalSort() {
        val values = arrayOf(
            Prioritized(5, "V"),
            Prioritized(10, "X"),
            Prioritized(11, "XI"),
            Prioritized(10, "X-2"),
            Prioritized(14, "XIV"),
        )
        values.sort()

        assertEquals("XIV", values[0].value)
        assertEquals("XI", values[1].value)
        assertEquals("X", values[2].value)
        assertEquals("X-2", values[3].value)
        assertEquals("V", values[4].value)
    }
}
