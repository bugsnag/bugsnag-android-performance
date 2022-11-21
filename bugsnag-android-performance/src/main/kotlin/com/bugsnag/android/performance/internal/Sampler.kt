package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Span

class Sampler(fallbackProbability: Double) {
    var fallbackProbability = fallbackProbability
        set(value) {
            field = value
            currentProbability = value
            expiryTime = newExpiryTime()
        }
    private var expiryTime = newExpiryTime()
    private var currentProbability = fallbackProbability
    var probability: Double
        get() {
            return when {
                System.currentTimeMillis() > expiryTime -> fallbackProbability
                else -> currentProbability
            }
        }
        set(value) {
            currentProbability = value
            expiryTime = newExpiryTime()
        }

    private fun shouldKeep(samplingValue: Double, upperBound: Double): Boolean {
        return upperBound > 0.0 && samplingValue <= upperBound
    }

    fun sampled(spans: Collection<Span>): Collection<Span> {
        val p = probability
        return spans.filter {
            if (shouldKeep(it.samplingValue, p)) {
                it.samplingProbability = p
                true
            } else {
                false
            }
        }

    }

    fun sampleShouldKeep(span: Span): Boolean {
        val p = probability
        span.samplingProbability = p
        return shouldKeep(span.samplingValue, p)
    }

    companion object {
        fun newExpiryTime() = System.currentTimeMillis() + InternalDebug.pValueExpireAfterMs
    }
}
