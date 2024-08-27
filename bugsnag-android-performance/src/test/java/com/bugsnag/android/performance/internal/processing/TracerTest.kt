package com.bugsnag.android.performance.internal.processing

import com.bugsnag.android.performance.SpanEndCallback
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.Worker
import com.bugsnag.android.performance.test.withDebugValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TracerTest {
    private lateinit var tracer: Tracer
    private lateinit var spanFactory: SpanFactory
    private val spanEndCallback1 = SpanEndCallback { true }
    private val spanEndCallback2 = SpanEndCallback { false }
    private val worker = mock<Worker>()

    @Test
    fun testBatchSize() = InternalDebug.withDebugValues {
        createTracer(arrayOf(spanEndCallback1))
        spanFactory = SpanFactory(tracer)
        InternalDebug.spanBatchSizeSendTriggerPoint = 2

        spanFactory.createCustomSpan("BatchSize1.1", SpanOptions.startTime(1L)).end(10L)

        // assert it won't be delivered immediately (the batch size is 2)
        assertNull(tracer.collectNextBatch())
        spanFactory.createCustomSpan("BatchSize1.2", SpanOptions.startTime(1L)).end(10L)
        // give the delivery thread time to wake up and do it's work
        assertEquals(2, tracer.collectNextBatch()!!.size)

        // ensure that the next batch is null - since there is now no data to be delivered
        assertNull(tracer.collectNextBatch())

        // we deliver another two to ensure that the loop behaves as expected
        spanFactory.createCustomSpan("BatchSize2.1", SpanOptions.startTime(2L)).end(20L)
        spanFactory.createCustomSpan("BatchSize2.2", SpanOptions.startTime(3L)).end(30L)

        assertEquals(2, tracer.collectNextBatch()!!.size)
    }

    @Test
    fun activatesWorker() = InternalDebug.withDebugValues {
        createTracer(arrayOf(spanEndCallback1))
        spanFactory = SpanFactory(tracer)
        InternalDebug.spanBatchSizeSendTriggerPoint = 2
        spanFactory.createCustomSpan("BatchSize1.1", SpanOptions.startTime(2L)).end(20L)
        spanFactory.createCustomSpan("BatchSize1.2", SpanOptions.startTime(3L)).end(30L)

        // ensure that 2 spans woke the worker up exactly once
        verify(worker, times(1)).wake()
    }

    @Test
    fun emptyBatch() = InternalDebug.withDebugValues {
        createTracer(arrayOf(spanEndCallback1))
        spanFactory = SpanFactory(tracer)
        InternalDebug.workerSleepMs = 0L
        val batch = tracer.collectNextBatch()
        assertEquals(0, batch?.size) // we expect an empty batch, not null
    }

    @Test
    fun emptySpanEndCallback() {
        createTracer(emptyArray())
        spanFactory = SpanFactory(tracer)
        InternalDebug.spanBatchSizeSendTriggerPoint = 1
        spanFactory.createCustomSpan("BatchSize1.1", SpanOptions.startTime(2L)).end(20L)
        verify(worker, times(1)).wake()
    }

    @Test
    fun spanEndCallbackReturnFalse() {
        createTracer(arrayOf(spanEndCallback1, spanEndCallback2))
        spanFactory = SpanFactory(tracer)
        InternalDebug.spanBatchSizeSendTriggerPoint = 1
        val span = spanFactory.createCustomSpan("BatchSize1.1", SpanOptions.startTime(2L)).apply {
            end()
        }
        assertNull(span.attributes["bugsnag.span.callbacks_duration"])
        verify(worker, times(0)).wake()
    }

    @Test
    fun spanEndCallbackDuration() {
        createTracer(arrayOf(spanEndCallback1))
        spanFactory = SpanFactory(tracer)
        InternalDebug.spanBatchSizeSendTriggerPoint = 1
        val span = spanFactory.createCustomSpan("BatchSize1.1", SpanOptions.startTime(2L)).apply {
            end()
        }

        assertNotNull(span.attributes["bugsnag.span.callbacks_duration"])
        verify(worker, times(1)).wake()
    }

    private fun createTracer(spanEndCallbacks: Array<SpanEndCallback>) {
        tracer = Tracer(spanEndCallbacks)
        tracer.worker = worker
    }
}
