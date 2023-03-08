package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.test.CollectingSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ProbabilitySamplerTest {
    val spanFactory = TestSpanFactory()
    val spanProcessor = CollectingSpanProcessor()

    // Spans use the upper long of their traceId (a UUID) to generate a "random" sampling value.
    private fun uuidWithUpper(upper: Long): UUID {
        return UUID(upper, 0)
    }

    @Test
    fun testSampleSpanProbability1() {
        val sampler = ProbabilitySampler(1.0)
        var span = spanFactory.newSpan(processor = spanProcessor, traceId = uuidWithUpper(0))
        assertTrue(sampler.shouldKeepSpan(span))
        span = spanFactory.newSpan(
            processor = spanProcessor,
            traceId = uuidWithUpper(Long.MAX_VALUE shl 1),
        )
        assertTrue(sampler.shouldKeepSpan(span))
        span = spanFactory.newSpan(
            processor = spanProcessor,
            traceId = uuidWithUpper(Long.MAX_VALUE),
        )
        assertTrue(sampler.shouldKeepSpan(span))
        span = spanFactory.newSpan(
            processor = spanProcessor,
            traceId = uuidWithUpper(1000000000),
        )
        assertTrue(sampler.shouldKeepSpan(span))
    }

    @Test
    fun testSampleSpanProbability0() {
        val sampler = ProbabilitySampler(0.0)
        var span = spanFactory.newSpan(
            processor = spanProcessor,
            traceId = uuidWithUpper(0),
        )
        assertTrue(!sampler.shouldKeepSpan(span))
        span = spanFactory.newSpan(
            processor = spanProcessor,
            traceId = uuidWithUpper(Long.MAX_VALUE shl 1),
        )
        assertTrue(!sampler.shouldKeepSpan(span))
        span = spanFactory.newSpan(
            processor = spanProcessor,
            traceId = uuidWithUpper(Long.MAX_VALUE),
        )
        assertTrue(!sampler.shouldKeepSpan(span))
        span = spanFactory.newSpan(
            processor = spanProcessor,
            traceId = uuidWithUpper(1000000000),
        )
        assertTrue(!sampler.shouldKeepSpan(span))
    }

    @Test
    fun testSampleSpanProbability0_5() {
        val sampler = ProbabilitySampler(0.5)
        var span = spanFactory.newSpan(
            processor = spanProcessor,
            traceId = uuidWithUpper(0),
        )
        assertTrue(sampler.shouldKeepSpan(span))
        span = spanFactory.newSpan(
            processor = spanProcessor,
            traceId = uuidWithUpper(Long.MAX_VALUE shl 1),
        )
        assertTrue(!sampler.shouldKeepSpan(span))
        span = spanFactory.newSpan(
            processor = spanProcessor,
            traceId = uuidWithUpper(Long.MAX_VALUE - 10000),
        )
        assertTrue(sampler.shouldKeepSpan(span))
    }

    @Test
    fun testSampleSpanBatchProbability1() {
        val sampler = ProbabilitySampler(1.0)
        val batch = listOf(
            spanFactory.newSpan(processor = spanProcessor, traceId = uuidWithUpper(0)),
            spanFactory.newSpan(
                processor = spanProcessor,
                traceId = uuidWithUpper(Long.MAX_VALUE shl 1),
            ),
            spanFactory.newSpan(
                processor = spanProcessor,
                traceId = uuidWithUpper(Long.MAX_VALUE),
            ),
            spanFactory.newSpan(
                processor = spanProcessor,
                traceId = uuidWithUpper(1000000000),
            ),
        )
        val sampled = sampler.sampled(batch)
        assertTrue(sampled.size == 4)
    }

    @Test
    fun testSampleSpanBatchProbability0() {
        val sampler = ProbabilitySampler(0.0)
        val batch = listOf(
            spanFactory.newSpan(processor = spanProcessor, traceId = uuidWithUpper(0)),
            spanFactory.newSpan(
                processor = spanProcessor,
                traceId = uuidWithUpper(Long.MAX_VALUE shl 1),
            ),
            spanFactory.newSpan(processor = spanProcessor, traceId = uuidWithUpper(Long.MAX_VALUE)),
            spanFactory.newSpan(
                processor = spanProcessor,
                traceId = uuidWithUpper(1000000000),
            ),
        )
        val sampled = sampler.sampled(batch)
        assertEquals("expected all spans to be sampled", 0, sampled.size)
    }

    @Test
    fun testSampleSpanBatchProbability0_5() {
        val sampler = ProbabilitySampler(0.5)
        val batch = listOf(
            spanFactory.newSpan(processor = spanProcessor, traceId = uuidWithUpper(0)),
            spanFactory.newSpan(
                processor = spanProcessor,
                traceId = uuidWithUpper(Long.MAX_VALUE shl 1),
            ),
            spanFactory.newSpan(
                processor = spanProcessor,
                traceId = uuidWithUpper(Long.MAX_VALUE - 10000),
            ),
            spanFactory.newSpan(processor = spanProcessor, traceId = uuidWithUpper(1000000000)),
        )
        val sampled = sampler.sampled(batch)
        assertEquals(3, sampled.size)
    }
}