package com.bugsnag.android.performance

import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.test.task
import com.bugsnag.android.performance.test.testSpanProcessor
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
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

    @Test
    fun spansClosedInOrder() {
        createTestSpan().use { spanA ->
            assertSame(spanA, SpanContext.current)
            createTestSpan().use { spanB ->
                assertSame(spanB, SpanContext.current)
                createTestSpan().use { spanC ->
                    assertSame(spanC, SpanContext.current)
                }
                assertSame(spanB, SpanContext.current)
            }
            assertSame(spanA, SpanContext.current)
        }
        assertSame(SpanContext.invalid, SpanContext.current)
    }

    @Test
    fun spansClosedOutOfOrder() {
        val spanA = createTestSpan()
        val spanB = createTestSpan()
        val spanC = createTestSpan()

        spanA.end(1L)
        spanB.end(2L)
        assertSame(spanC, SpanContext.current)

        spanC.end(3L)
        assertSame(SpanContext.invalid, SpanContext.current)
    }

    private fun createTestSpan() = SpanImpl(
        name = "Test/test span",
        kind = SpanKind.INTERNAL,
        startTime = 0L,
        traceId = UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
        parentSpanId = 0L,
        processor = testSpanProcessor,
    )

    private data class TestSpanContext(
        override val spanId: Long,
        override val traceId: UUID,
        val threadId: Long
    ) : SpanContext
}
