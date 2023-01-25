package com.bugsnag.android.performance.internal

import androidx.annotation.FloatRange
import com.bugsnag.android.performance.internal.SpanImpl

internal class Sampler(
    /**
     * The probability that any given [Span] is retained during sampling as a value between 0 and 1.
     */
    @FloatRange(from = 0.0, to = 1.0)
    var sampleProbability: Double
) {
    fun sampled(spans: Collection<SpanImpl>): Collection<SpanImpl> {
        return spans.filter { shouldKeepSpan(it) }
    }

    // Side effect: Sets span.samplingProbability to the current probability
    fun shouldKeepSpan(span: SpanImpl): Boolean {
        val upperBound = sampleProbability
        span.samplingProbability = upperBound
        return upperBound > 0.0 && span.samplingValue <= upperBound
    }
}
