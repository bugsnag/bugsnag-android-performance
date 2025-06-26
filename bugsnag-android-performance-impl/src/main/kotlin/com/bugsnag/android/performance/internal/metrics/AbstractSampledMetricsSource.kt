package com.bugsnag.android.performance.internal.metrics

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.SpanImpl
import java.util.concurrent.atomic.AtomicReference

internal abstract class AbstractSampledMetricsSource<T : LinkedMetricsSnapshot<T>>(
    private val samplingDelayMs: Long,
) : SampledMetricSource<T> {
    /**
     * Chain/linked-list of snapshots awaiting the "next" sample, stored as an AtomicReference
     * in the same way as we deal with [SpanImpl] batching (see [BatchingSpanProcessor]) giving
     * us a lightweight way to collect spans that are waiting for a sample.
     */
    private val awaitingNextSample = AtomicReference<T?>(null)

    protected abstract fun captureSample()

    protected abstract fun populatedWaitingTargets()

    override fun run() {
        captureSample()
        populatedWaitingTargets()
    }

    protected fun takeSnapshotsAwaitingSample(): T? {
        return awaitingNextSample.getAndSet(null)
    }

    protected inline fun T?.forEach(consumer: (T) -> Unit) {
        var snapshot = this

        while (snapshot != null) {
            consumer(snapshot)
            snapshot = snapshot.next
        }
    }

    override fun endMetrics(
        startMetrics: T,
        span: Span,
    ) {
        startMetrics.target = span as SpanImpl
        startMetrics.blocking = span.block(samplingDelayMs * 2)

        while (true) {
            val next = awaitingNextSample.get()
            startMetrics.next = next

            if (awaitingNextSample.compareAndSet(next, startMetrics)) {
                break
            }
        }
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class LinkedMetricsSnapshot<T : LinkedMetricsSnapshot<T>> {
    @JvmField
    public var next: T? = null

    @JvmField
    public var target: SpanImpl? = null

    @JvmField
    public var blocking: SpanImpl.Condition? = null
}
