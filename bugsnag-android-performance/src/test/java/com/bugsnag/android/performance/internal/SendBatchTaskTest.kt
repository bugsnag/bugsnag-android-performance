package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.internal.processing.Tracer
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class SendBatchTaskTest {

    @Before
    fun stubLogger() {
        Logger.delegate = NoopLogger
    }

    @After
    fun unStubLogger() {
        Logger.delegate = DebugLogger
    }

    @Test
    fun sendBatch() {
        val spanFactory = TestSpanFactory()
        val tracer = mock<Tracer> {
            on { collectNextBatch() } doReturn spanFactory.newSpans(10, NoopSpanProcessor)
        }

        val delivery = mock<Delivery>()

        val resourceAttributes = Attributes()
        val sendBatchTask = SendBatchTask(delivery, tracer, resourceAttributes)
        val workDone = sendBatchTask.execute()

        assertTrue("SendBatchTask should have delivered a batch", workDone)
        verify(delivery).deliver(argWhere { it.size == 10 }, eq(resourceAttributes))
    }

    @Test
    fun noBatchToSend() {
        val tracer = mock<Tracer> {
            on { collectNextBatch() } doReturn null
        }

        val deliver = mock<Delivery>()

        val sendBatchTask = SendBatchTask(deliver, tracer, Attributes())
        val workDone = sendBatchTask.execute()

        assertFalse("SendBatchTask should not have done any work", workDone)
        verifyNoInteractions(deliver)
    }

    @Test
    fun emptyBatch() {
        val tracer = mock<Tracer> {
            on { collectNextBatch() } doReturn emptyList()
        }

        val delivery = mock<Delivery>()

        val resourceAttributes = Attributes()
        val sendBatchTask = SendBatchTask(delivery, tracer, resourceAttributes)
        val workDone = sendBatchTask.execute()

        assertFalse("SendBatchTask should not have done any work", workDone)
        verify(delivery).deliver(any<List<SpanImpl>>(), eq(resourceAttributes))
    }
}
