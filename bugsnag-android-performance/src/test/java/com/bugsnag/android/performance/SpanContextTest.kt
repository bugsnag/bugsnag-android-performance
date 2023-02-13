package com.bugsnag.android.performance

import com.bugsnag.android.performance.test.task
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.util.UUID

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
                task {
                    val threadId = Thread.currentThread().id
                    val expectedStack = mutableListOf<SpanContext>()
                    repeat(1000) {
                        val newContext = TestSpanContext(it.toLong(), UUID.randomUUID(), threadId)
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
            .forEach { it.get() }
    }

    private data class TestSpanContext(
        override val spanId: Long,
        override val traceId: UUID,
        val threadId: Long
    ) : SpanContext
}
