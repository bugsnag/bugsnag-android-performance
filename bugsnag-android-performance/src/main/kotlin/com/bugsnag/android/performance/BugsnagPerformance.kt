package com.bugsnag.android.performance

import android.os.SystemClock
import android.util.Log
import com.bugsnag.android.performance.internal.Delivery
import com.bugsnag.android.performance.internal.Tracer
import java.util.UUID

object BugsnagPerformance {
    private val tracer = Tracer()
    private var isStarted = false

    @JvmStatic
    fun start(configuration: PerformanceConfiguration) {
        if (!isStarted) {
            synchronized(this) {
                if (!isStarted) {
                    startUnderLock(configuration)
                    isStarted = true
                }
            }
        } else {
            logAlreadyStarted()
        }
    }

    private fun logAlreadyStarted() {
        Log.w("Bugsnag", "BugsnagPerformance.start has already been called")
    }

    private fun startUnderLock(configuration: PerformanceConfiguration) {
        tracer.start(
            Delivery(configuration.endpoint),
            configuration.context.packageName
        )
    }

    @JvmStatic
    @JvmOverloads
    fun startSpan(name: String, startTime: Long = SystemClock.elapsedRealtimeNanos()): Span =
        Span(
            name = "Custom/$name",
            kind = SpanKind.INTERNAL,
            startTime = startTime,
            traceId = UUID.randomUUID(),
            processor = tracer
        )
}

inline fun <R> measureSpan(name: String, block: () -> R): R {
    return BugsnagPerformance.startSpan(name).use { block() }
}
