package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Span
import java.util.Calendar
import java.util.Date

class Sampler(fallbackProbability: Double) {
    var fallbackProbability = fallbackProbability
    set(value) {
        field = value
        currentProbability = value
        expiryTime = oneDayFromNow()
    }
    private var expiryTime = oneDayFromNow()
    private var currentProbability = fallbackProbability
    var probability: Double
    get() {
        return when {
            Date().after(expiryTime) -> fallbackProbability
            else -> currentProbability
        }
    }
    set(value) {
        currentProbability = value
        expiryTime = oneDayFromNow()
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
        fun oneDayFromNow(): Date {
            Calendar.getInstance().apply {
                time = Date()
                add(Calendar.DAY_OF_YEAR, 1)
                return time
            }
        }
    }
}
