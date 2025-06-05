package com.bugsnag.android.performance

import java.util.concurrent.Callable
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * A [SpanContext] aware drop-in replacement for [ScheduledThreadPoolExecutor].
 * Sets the current [SpanContext] within a task to the [SpanContext] that was active
 * when the task was scheduled.
 */
public class ContextAwareScheduledThreadPoolExecutor : ScheduledThreadPoolExecutor {
    public constructor(corePoolSize: Int) : super(corePoolSize)
    public constructor(corePoolSize: Int, threadFactory: ThreadFactory?) : super(
        corePoolSize,
        threadFactory,
    )

    public constructor(corePoolSize: Int, handler: RejectedExecutionHandler?) : super(
        corePoolSize,
        handler,
    )

    public constructor(
        corePoolSize: Int,
        threadFactory: ThreadFactory?,
        handler: RejectedExecutionHandler?,
    ) : super(corePoolSize, threadFactory, handler)

    override fun schedule(
        command: Runnable?,
        delay: Long,
        unit: TimeUnit?,
    ): ScheduledFuture<*> {
        return super.schedule(command?.let { SpanContext.current.wrap(it) }, delay, unit)
    }

    override fun <V : Any?> schedule(
        callable: Callable<V>?,
        delay: Long,
        unit: TimeUnit?,
    ): ScheduledFuture<V> {
        return super.schedule(callable?.let { SpanContext.current.wrap(it) }, delay, unit)
    }

    override fun scheduleWithFixedDelay(
        command: Runnable?,
        initialDelay: Long,
        delay: Long,
        unit: TimeUnit?,
    ): ScheduledFuture<*> {
        return super.scheduleWithFixedDelay(command?.let { SpanContext.current.wrap(it) }, initialDelay, delay, unit)
    }

    override fun scheduleAtFixedRate(
        command: Runnable?,
        initialDelay: Long,
        period: Long,
        unit: TimeUnit?,
    ): ScheduledFuture<*> {
        return super.scheduleAtFixedRate(command?.let { SpanContext.current.wrap(it) }, initialDelay, period, unit)
    }
}
