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
        var span = spanFactory.newSpan(traceId = uuidWithUpper(0), processor = spanProcessor)
        assertTrue(sampler.shouldKeepSpan(span))
        span =
            spanFactory.newSpan(
                traceId = uuidWithUpper(Long.MAX_VALUE shl 1),
                processor = spanProcessor,
            )
        assertTrue(sampler.shouldKeepSpan(span))
        span =
            spanFactory.newSpan(
                traceId = uuidWithUpper(Long.MAX_VALUE),
                processor = spanProcessor,
            )
        assertTrue(sampler.shouldKeepSpan(span))
        span =
            spanFactory.newSpan(
                traceId = uuidWithUpper(1000000000),
                processor = spanProcessor,
            )
        assertTrue(sampler.shouldKeepSpan(span))
    }

    @Test
    fun testSampleSpanProbability0() {
        val sampler = ProbabilitySampler(0.0)
        var span =
            spanFactory.newSpan(
                traceId = uuidWithUpper(0),
                processor = spanProcessor,
            )
        assertTrue(!sampler.shouldKeepSpan(span))
        span =
            spanFactory.newSpan(
                traceId = uuidWithUpper(Long.MAX_VALUE shl 1),
                processor = spanProcessor,
            )
        assertTrue(!sampler.shouldKeepSpan(span))
        span =
            spanFactory.newSpan(
                traceId = uuidWithUpper(Long.MAX_VALUE),
                processor = spanProcessor,
            )
        assertTrue(!sampler.shouldKeepSpan(span))
        span =
            spanFactory.newSpan(
                traceId = uuidWithUpper(1000000000),
                processor = spanProcessor,
            )
        assertTrue(!sampler.shouldKeepSpan(span))
    }

    @Test
    fun testSampleSpanProbability0_5() {
        val sampler = ProbabilitySampler(0.5)
        var span =
            spanFactory.newSpan(
                traceId = uuidWithUpper(0),
                processor = spanProcessor,
            )
        assertTrue(sampler.shouldKeepSpan(span))
        span =
            spanFactory.newSpan(
                traceId = uuidWithUpper(Long.MAX_VALUE shl 1),
                processor = spanProcessor,
            )
        assertTrue(!sampler.shouldKeepSpan(span))
        span =
            spanFactory.newSpan(
                traceId = uuidWithUpper(Long.MAX_VALUE - 10000),
                processor = spanProcessor,
            )
        assertTrue(sampler.shouldKeepSpan(span))
    }

    @Test
    fun testSampleSpanBatchProbability1() {
        val sampler = ProbabilitySampler(1.0)
        val batch =
            listOf(
                spanFactory.newSpan(traceId = uuidWithUpper(0), processor = spanProcessor),
                spanFactory.newSpan(
                    traceId = uuidWithUpper(Long.MAX_VALUE shl 1),
                    processor = spanProcessor,
                ),
                spanFactory.newSpan(
                    traceId = uuidWithUpper(Long.MAX_VALUE),
                    processor = spanProcessor,
                ),
                spanFactory.newSpan(
                    traceId = uuidWithUpper(1000000000),
                    processor = spanProcessor,
                ),
            )
        val sampled = sampler.sampled(batch)
        assertTrue(sampled.size == 4)
    }

    @Test
    fun testSampleSpanBatchProbability0() {
        val sampler = ProbabilitySampler(0.0)
        val batch =
            listOf(
                spanFactory.newSpan(traceId = uuidWithUpper(0), processor = spanProcessor),
                spanFactory.newSpan(
                    traceId = uuidWithUpper(Long.MAX_VALUE shl 1),
                    processor = spanProcessor,
                ),
                spanFactory.newSpan(traceId = uuidWithUpper(Long.MAX_VALUE), processor = spanProcessor),
                spanFactory.newSpan(
                    traceId = uuidWithUpper(1000000000),
                    processor = spanProcessor,
                ),
            )
        val sampled = sampler.sampled(batch)
        assertEquals("expected all spans to be sampled", 0, sampled.size)
    }

    @Test
    fun testSampleSpanBatchProbability0_5() {
        val sampler = ProbabilitySampler(0.5)
        val batch =
            listOf(
                spanFactory.newSpan(traceId = uuidWithUpper(0), processor = spanProcessor),
                spanFactory.newSpan(
                    traceId = uuidWithUpper(Long.MAX_VALUE shl 1),
                    processor = spanProcessor,
                ),
                spanFactory.newSpan(
                    traceId = uuidWithUpper(Long.MAX_VALUE - 10000),
                    processor = spanProcessor,
                ),
                spanFactory.newSpan(traceId = uuidWithUpper(1000000000), processor = spanProcessor),
            )
        val sampled = sampler.sampled(batch)
        assertEquals(3, sampled.size)
    }

    @Test
    fun testSpanReducedProbability() {
        val sampler = ProbabilitySampler(0.25)
        val span =
            spanFactory.newSpan(
                traceId = uuidWithUpper(0),
                processor = spanProcessor,
            )

        span.samplingProbability = 1.0

        // when the sampler has lower probability, the span samplingProbability should be reduced
        assertTrue(sampler.shouldKeepSpan(span))
        assertEquals(0.25, span.samplingProbability, 0.001)
    }

    @Test
    fun testSpanIncreaseProbability() {
        val sampler = ProbabilitySampler(1.0)
        val span =
            spanFactory.newSpan(
                traceId = uuidWithUpper(0),
                processor = spanProcessor,
            )

        // when the sampler has higher probability, the span samplingProbability should stay the same
        span.samplingProbability = 0.4

        assertTrue(sampler.shouldKeepSpan(span))
        assertEquals(0.4, span.samplingProbability, 0.01)
    }
}
