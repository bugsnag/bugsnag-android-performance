package com.bugsnag.android.performance

import com.bugsnag.android.performance.internal.context.ContextAwareThreadFactory
import java.util.concurrent.BlockingQueue
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * A [SpanContext] aware drop-in replacement for [ThreadPoolExecutor].
 * Sets the current [SpanContext] within a task to the [SpanContext] that was active
 * when the task was submitted.
 */
public class ContextAwareThreadPoolExecutor : ThreadPoolExecutor {
    public constructor(
        corePoolSize: Int,
        maximumPoolSize: Int,
        keepAliveTime: Long,
        unit: TimeUnit?,
        workQueue: BlockingQueue<Runnable>?,
    ) : super(
        corePoolSize,
        maximumPoolSize,
        keepAliveTime,
        unit,
        workQueue,
        ContextAwareThreadFactory.defaultContextAwareThreadFactory,
    )

    public constructor(
        corePoolSize: Int,
        maximumPoolSize: Int,
        keepAliveTime: Long,
        unit: TimeUnit?,
        workQueue: BlockingQueue<Runnable>?,
        threadFactory: ThreadFactory?,
    ) : super(
        corePoolSize,
        maximumPoolSize,
        keepAliveTime,
        unit,
        workQueue,
        ContextAwareThreadFactory.wrap(threadFactory),
    )

    public constructor(
        corePoolSize: Int,
        maximumPoolSize: Int,
        keepAliveTime: Long,
        unit: TimeUnit?,
        workQueue: BlockingQueue<Runnable>?,
        handler: RejectedExecutionHandler?,
    ) : super(
        corePoolSize,
        maximumPoolSize,
        keepAliveTime,
        unit,
        workQueue,
        ContextAwareThreadFactory.defaultContextAwareThreadFactory,
        handler,
    )

    @Suppress("LongParameterList")
    public constructor(
        corePoolSize: Int,
        maximumPoolSize: Int,
        keepAliveTime: Long,
        unit: TimeUnit?,
        workQueue: BlockingQueue<Runnable>?,
        threadFactory: ThreadFactory?,
        handler: RejectedExecutionHandler?,
    ) : super(
        corePoolSize,
        maximumPoolSize,
        keepAliveTime,
        unit,
        workQueue,
        ContextAwareThreadFactory.wrap(threadFactory),
        handler,
    )

    override fun execute(command: Runnable?) {
        super.execute(command?.let { SpanContext.current.wrap(it) })
    }
}
