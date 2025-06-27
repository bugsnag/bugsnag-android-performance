package com.bugsnag.android.performance.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpanStateTest {
    @Test
    fun simple() {
        val state = SpanState()
        assertOpen(state)
        assertTrue(state.ending())
        assertTrue(state.end())
        assertEnded(state)
        assertTrue(state.process())
    }

    @Test
    fun discardOpen() {
        val state = SpanState()
        assertOpen(state)
        assertTrue(state.discard())
        assertDiscarded(state)
        assertFalse(state.process())
    }

    @Test
    fun discardInEnd() {
        val state = SpanState()
        assertOpen(state)
        assertTrue(state.ending())
        assertTrue(state.discard())
        assertDiscarded(state)
        assertFalse(state.end())
        assertDiscarded(state)
        assertFalse(state.process())
    }

    @Test
    fun blockOpen() {
        val state = SpanState()
        assertOpen(state)
        assertTrue(state.block())
        assertTrue(state.ending())
        assertTrue(state.end())
        assertEndedBlocked(state)
    }

    @Test
    fun blockInEnd() {
        val state = SpanState()
        assertOpen(state)
        assertTrue(state.ending())
        assertTrue(state.block())
        assertTrue(state.end())
        assertEndedBlocked(state)
    }

    @Test
    fun fail_discardAfterEnd() {
        val state = SpanState()
        assertOpen(state)
        assertTrue(state.ending())
        assertTrue(state.end())

        assertFalse("span has ended and cannot be discarded", state.discard())
    }

    @Test
    fun fail_blockAfterEnd() {
        val state = SpanState()
        assertOpen(state)
        assertTrue(state.ending())
        assertTrue(state.end())

        assertFalse("span has ended and cannot be blocked", state.block())
    }

    @Test
    fun fail_discardAndBlock() {
        val state = SpanState()
        assertOpen(state)
        assertTrue(state.discard())
        assertFalse(state.block())
    }

    @Test
    fun fail_discardAndEnd() {
        val state = SpanState()
        assertOpen(state)
        assertTrue(state.discard())
        assertFalse(state.ending())
        assertFalse(state.end())
    }

    private fun assertOpen(state: SpanState) {
        assertTrue("state should be open: $state", state.isOpen)
        assertFalse("state should not be discarded: $state", state.isDiscarded)
        assertFalse("state should not be blocked: $state", state.isBlocked)
    }

    private fun assertEnded(state: SpanState) {
        assertFalse("state should not be open: $state", state.isOpen)
        assertFalse("state should not be discarded: $state", state.isDiscarded)
        assertFalse("state should not be blocked: $state", state.isBlocked)
    }

    private fun assertDiscarded(state: SpanState) {
        assertTrue("state should be discarded: $state", state.isDiscarded)
        assertFalse("state should not be open: $state", state.isOpen)
        assertFalse("state should not be blocked: $state", state.isBlocked)
    }

    private fun assertEndedBlocked(state: SpanState) {
        assertTrue("state should be blocked: $state", state.isBlocked)
        assertFalse("state should not be discarded: $state", state.isDiscarded)
        assertFalse("state should not be open: $state", state.isOpen)
    }
}
