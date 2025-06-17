package com.bugsnag.android.performance.internal

import androidx.annotation.FloatRange

internal interface Sampler {
    fun sampled(spans: Collection<SpanImpl>): Collection<SpanImpl> {
        return spans.filter { shouldKeepSpan(it) }
    }

    fun shouldKeepSpan(span: SpanImpl): Boolean

    /**
     * The probability that any given [Span] is retained during sampling as a value between 0 and 1.
     * Defaults to 1.0 but the implementation should override this value to return an equivalent
     * value to the current sampling probability (if possible).
     */
    val sampleProbability: Double
        get() = 1.0
}

/**
 * A Sampler implementation that discards all Spans used when the SDK is configured in a
 * disabled releaseStage.
 */
internal object DiscardingSampler : Sampler {
    override fun sampled(spans: Collection<SpanImpl>): Collection<SpanImpl> = emptyList()

    override fun shouldKeepSpan(span: SpanImpl): Boolean = false

    override val sampleProbability: Double get() = 0.0
}

internal class ProbabilitySampler(
    /**
     * The probability that any given [Span] is retained during sampling as a value between 0 and 1.
     */
    @FloatRange(from = 0.0, to = 1.0)
    override var sampleProbability: Double,
) : Sampler {
    // Side effect: Sets span.samplingProbability to the current probability
    override fun shouldKeepSpan(span: SpanImpl): Boolean {
        val upperBound = sampleProbability
        // only change the span samplingProbability if the new probability is lower
        if (upperBound < span.samplingProbability) {
            span.samplingProbability = upperBound
        }

        return upperBound > 0.0 && span.samplingValue <= upperBound
    }
}
