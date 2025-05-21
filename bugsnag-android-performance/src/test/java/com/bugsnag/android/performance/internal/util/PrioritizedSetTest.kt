package com.bugsnag.android.performance.internal.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrioritizedSetTest {
    @Test
    fun testEmptySet() {
        val set = PrioritizedSet<String>()
        assertEquals(0, set.size)
    }

    @Test
    fun testAddingSingleElement() {
        val set = PrioritizedSet<String>()
        assertTrue(set.add(Prioritized(1, "test")))
        assertEquals(1, set.size)
    }

    @Test
    fun testDuplicateValueIsIgnored() {
        val set = PrioritizedSet<String>()
        assertTrue(set.add(Prioritized(1, "test")))
        assertFalse(set.add(Prioritized(2, "test"))) // Same value, different priority
        assertEquals(1, set.size)
    }

    @Test
    fun testPriorityOrdering() {
        val set = PrioritizedSet<String>()
        set.add(Prioritized(1, "low"))
        set.add(Prioritized(3, "high"))
        set.add(Prioritized(2, "medium"))

        val values = mutableListOf<String>()
        set.forEach { values.add(it) }

        assertEquals(listOf("high", "medium", "low"), values)
    }

    @Test
    fun testAddAllWithDuplicates() {
        val set = PrioritizedSet<String>()
        set.add(Prioritized(1, "existing"))

        val newElements = listOf(
            Prioritized(2, "existing"), // Should be ignored
            Prioritized(3, "new1"),
            Prioritized(4, "new2"),
        )

        assertTrue(set.addAll(newElements))
        assertEquals(3, set.size)

        val values = mutableListOf<String>()
        set.forEach { values.add(it) }
        assertEquals(listOf("new2", "new1", "existing"), values)
    }

    @Test
    fun testAddAllWithAllDuplicates() {
        val set = PrioritizedSet<String>()
        set.add(Prioritized(1, "test"))

        val newElements = listOf(
            Prioritized(2, "test"),
            Prioritized(3, "test"),
        )

        assertFalse(set.addAll(newElements))
        assertEquals(1, set.size)
    }

    @Test
    fun testAddAllEmpty() {
        val set = PrioritizedSet<String>()
        assertFalse(set.addAll(emptyList()))
        assertEquals(0, set.size)
    }

    @Test
    fun testSamePriorityOrdering() {
        val set = PrioritizedSet<String>()
        set.add(Prioritized(0, "fourth"))
        set.add(Prioritized(1, "first"))
        set.add(Prioritized(0, "fifth"))
        set.add(Prioritized(1, "second"))
        set.add(Prioritized(5, "zero"))
        set.add(Prioritized(1, "third"))

        val values = mutableListOf<String>()
        set.forEach { values.add(it) }
        assertEquals(6, set.size)
        assertEquals(listOf("zero", "first", "second", "third", "fourth", "fifth"), values)
    }
}
