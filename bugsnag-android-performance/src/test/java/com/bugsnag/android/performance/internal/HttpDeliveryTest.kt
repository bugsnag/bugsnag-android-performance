package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.test.CollectingSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HttpDeliveryTest {
    val spanFactory = TestSpanFactory()
    val spanProcessor = CollectingSpanProcessor()

    @Test
    fun noConnectivity() {
        val connectivity = mock<Connectivity> {
            on { hasConnection } doReturn false
        }
        val delivery = HttpDelivery("http://localhost", "0123456789abcdef0123456789abcdef", connectivity)

        val spans = spanFactory.newSpans(5, spanProcessor)
        val result = delivery.deliver(spans, Attributes())

        Assert.assertTrue(result is DeliveryResult.Failed)
        val failed = (result as DeliveryResult.Failed)
        Assert.assertTrue(failed.canRetry)
    }
}
