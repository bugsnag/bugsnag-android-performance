package com.bugsnag.android.performance

import android.os.SystemClock
import java.util.UUID

object BugsnagPerformance {
    private lateinit var tracer: Tracer

    @JvmStatic
    @JvmOverloads
    fun start(configuration: BugsnagPerformanceConfiguration = BugsnagPerformanceConfiguration()) {
        tracer = Tracer(configuration.endpoint.toString())

        Thread(tracer, "Bugsnag Tracer").apply {
            isDaemon = true
            start()
        }
    }

    @JvmStatic
    @JvmOverloads
    fun startSpan(name: String, startTime: Long = SystemClock.elapsedRealtimeNanos()): Span =
        SpanImpl(name, SpanKind.INTERNAL, startTime, UUID.randomUUID(), processor = tracer)
}

inline fun <R> measureSpan(name: String, block: () -> R): R {
    return BugsnagPerformance.startSpan(name).use { block() }
}
