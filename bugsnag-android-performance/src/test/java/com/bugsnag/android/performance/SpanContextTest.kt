package com.bugsnag.android.performance

import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.concurrent.thread

internal class SpanContextTest {
    @Before
    fun ensureContextClear() {
        while (SpanContext.current != SpanContext.invalid) {
            SpanContext.detach(SpanContext.current)
        }
    }

    @Test
    fun invalidSpanContext() {
        assertSame(SpanContext.invalid, SpanContext.current)
    }

    @Test
    fun testThreadedContext() {
        (0..5)
            .map {
                thread {
                    val expectedStack = mutableListOf<SpanContext>()
                    repeat(10) {
                        val newContext = TestSpanContext(it.toLong())
                        SpanContext.attach(newContext)
                        expectedStack.add(newContext)

                        assertSame(newContext, SpanContext.current)
                    }

                    expectedStack.reversed().forEach { expectedContext ->
                        assertSame(expectedContext, SpanContext.current)
                        SpanContext.detach(expectedContext)
                    }
                }
            }
            .forEach { it.join() }
    }

    private data class TestSpanContext(
        override val spanId: Long,
        override val traceId: UUID = UUID.randomUUID()
    ) : SpanContext
}