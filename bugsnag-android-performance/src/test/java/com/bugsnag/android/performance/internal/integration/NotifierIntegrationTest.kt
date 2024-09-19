package com.bugsnag.android.performance.internal.integration

import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Event
import com.bugsnag.android.OnErrorCallback
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.internal.SpanCategory
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.test.NoopSpanProcessor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.util.Random
import java.util.UUID

class NotifierIntegrationTest {
    @Test
    fun withStartedBugsnag() {
        val onErrorCaptor = argumentCaptor<OnErrorCallback>()
        val mockBugsnag = mockStatic(Bugsnag::class.java)
        try {
            mockBugsnag.`when`<Boolean> { Bugsnag.isStarted() } doReturn true
            mockBugsnag.`when`<Void> { Bugsnag.addOnError(onErrorCaptor.capture()) }
                .thenAnswer { null }
            NotifierIntegration.link()

            assertTrue(NotifierIntegration.linked)

            val traceId = UUID.randomUUID()
            val spanId = Random().nextLong()

            val mockEvent =
                createMockEventWithinSpan(createTestSpan(traceId, spanId), onErrorCaptor.firstValue)

            verify(mockEvent).setTraceCorrelation(traceId, spanId)
        } finally {
            mockBugsnag.close()
        }
    }

    @Test
    fun withUnlinkedBugsnag() {
        val mockBugsnag = mockStatic(Bugsnag::class.java)
        try {
            // we can't truly mock a classloader failure, but we can come pretty close
            mockBugsnag.`when`<Boolean> { Bugsnag.isStarted() } doThrow NoClassDefFoundError("Bugsnag")
            mockBugsnag.verify({ Bugsnag.addOnError(any()) }, never())
            NotifierIntegration.link()

            assertFalse(NotifierIntegration.linked)
        } finally {
            mockBugsnag.close()
        }
    }

    private fun createMockEventWithinSpan(
        span: SpanImpl,
        onErrorCallback: OnErrorCallback,
    ): Event {
        val mockEvent = mock<Event>()

        NotifierIntegration.onSpanStarted(span)
        assertTrue(onErrorCallback.onError(mockEvent))
        span.end(54321L)

        return mockEvent
    }

    private fun createTestSpan(
        traceId: UUID,
        spanId: Long,
        makeContext: Boolean = true,
    ) = SpanImpl(
        "Test Span",
        SpanCategory.CUSTOM,
        SpanKind.INTERNAL,
        1234L,
        traceId,
        spanId,
        0L,
        NoopSpanProcessor,
        makeContext,
        null,
    )
}
