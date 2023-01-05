package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Span

internal class Sampler(fallbackProbability: Double) {
    var fallbackProbability = fallbackProbability
        set(value) {
            field = value
            currentProbability = value
            expiryTime = newExpiryTime()
        }

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

    var expiryTime = newExpiryTime()
    private var currentProbability = fallbackProbability

    fun loadConfiguration(persistentState: PersistentState) {
        this.currentProbability = persistentState.pValue
        this.expiryTime = persistentState.pValueExpiryTime
    }

    fun sampled(spans: Collection<Span>): Collection<Span> {
        return spans.filter { shouldKeepSpan(it) }
    }

    // Side effect: Sets span.samplingProbability to the current probability
    fun shouldKeepSpan(span: Span): Boolean {
        val upperBound = probability
        span.samplingProbability = upperBound
        return upperBound > 0.0 && span.samplingValue <= upperBound
    }

    private fun newExpiryTime() = System.currentTimeMillis() + InternalDebug.pValueExpireAfterMs
}
