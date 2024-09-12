package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.test.NoopSpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import java.lang.Thread.sleep
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SpanTest {
    @Test
    fun nameImmutableAfterEnd() {
        val newSpanName = "edited test span"
        val span = createTestSpan()

        span.name = newSpanName
        span.end()

        // this should be silently ignored
        span.name = "cannot be renamed"

        // the name should not have changed
        assertEquals(newSpanName, span.name)
    }

    @Test
    fun idempotentEnd() {
        val mockSpanProcessor = mock<SpanProcessor>()
        val span = SpanImpl(
            "test span",
            SpanCategory.CUSTOM,
            SpanKind.INTERNAL,
            0L,
            UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
            0xdecafbad,
            0L,
            mockSpanProcessor,
            false,
            null,
        )

        span.end()

        val endTime = span.endTime

        // attempt to end the span again
        span.end(1234L)

        // check the endTime has not actually changed
        assertEquals(endTime, span.endTime)
        verify(mockSpanProcessor).onEnd(span)
    }

    @Test
    fun spanAsClosable() {
        val span = createTestSpan().apply { use { sleep(1L) } }
        assertNotEquals(SpanImpl.NO_END_TIME, span.endTime)
        assertTrue(span.startTime < span.endTime.get())
    }

    private fun createTestSpan() = SpanImpl(
        "Test/test span",
        SpanCategory.CUSTOM,
        SpanKind.INTERNAL,
        0L,
        UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
        0xdecafbad,
        0L,
        NoopSpanProcessor,
        false,
        null,
    )
}
