package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.test.StubDelivery
import com.bugsnag.android.performance.test.withDebugValues
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TracerTest {
    private lateinit var stubDelivery: StubDelivery
    private lateinit var tracer: Tracer

    @Before
    fun createTracer() {
        tracer = Tracer()
        stubDelivery = StubDelivery()
    }

    @After
    fun destroyTracer() {
        tracer.stop()
    }

    @Test
    fun testBatchTimeout() = InternalDebug.withDebugValues {
        InternalDebug.spanBatchTimeoutMs = 50

        val spanFactory = SpanFactory(tracer)

        tracer.start(
            PerformanceConfiguration(
                RuntimeEnvironment.getApplication(),
                "decafbaddecafbaddecafbaddecafbad"
            )
        )

        // swap the standard delivery for our stub
        tracer.delivery = stubDelivery

        spanFactory.createCustomSpan("BatchTimeout", 1L).end(10L)

        // assert it was not delivered immediately (timeout is 50ms)
        assertNull(stubDelivery.lastAttempt)
        stubDelivery.awaitDelivery()
        assertEquals(1, stubDelivery.lastAttempt?.size)

        stubDelivery.reset(DeliveryResult.SUCCESS)

        // we deliver another two to ensure that the loop behaves as expected
        spanFactory.createCustomSpan("BatchTimeout2", 2L).end(20L)
        spanFactory.createCustomSpan("BatchTimeout3", 3L).end(30L)

        stubDelivery.awaitDelivery()
        assertEquals(2, stubDelivery.lastAttempt?.size)
    }

    @Test
    fun testBatchSize() = InternalDebug.withDebugValues {
        InternalDebug.spanBatchSizeSendTriggerPoint = 2

        val spanFactory = SpanFactory(tracer)

        tracer.start(
            PerformanceConfiguration(
                RuntimeEnvironment.getApplication(),
                "decafbaddecafbaddecafbaddecafbad"
            )
        )

        // swap the standard delivery for our stub
        tracer.delivery = stubDelivery

        spanFactory.createCustomSpan("BatchSize1.1", 1L).end(10L)

        // assert it was not delivered immediately (the batch size is 2)
        assertNull(stubDelivery.lastAttempt)
        spanFactory.createCustomSpan("BatchSize1.2", 1L).end(10L)
        // give the delivery thread time to wake up and do it's work
        stubDelivery.awaitDelivery(500L)
        assertEquals(2, stubDelivery.lastAttempt?.size)

        stubDelivery.reset(DeliveryResult.SUCCESS)

        // we deliver another two to ensure that the loop behaves as expected
        spanFactory.createCustomSpan("BatchSize2.1", 2L).end(20L)
        spanFactory.createCustomSpan("BatchSize2.2", 3L).end(30L)

        stubDelivery.awaitDelivery(500L)
        assertEquals(2, stubDelivery.lastAttempt?.size)
    }
}
