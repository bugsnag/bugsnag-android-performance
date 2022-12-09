package com.bugsnag.android.performance.internal

import androidx.annotation.VisibleForTesting
import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.Span
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class Tracer : SpanProcessor, Runnable {
    @VisibleForTesting
    internal lateinit var delivery: Delivery

    private lateinit var serviceName: String
    private lateinit var resourceAttributes: Attributes

    internal val sampler = Sampler(1.0)

    @Suppress("DoubleMutabilityForCollection") // we swap out this ArrayList when we flush batches
    private var batch = ArrayList<Span>()

    private val lock = ReentrantLock(false)
    private val sendBatchCondition = lock.newCondition()

    private var runner: Thread? = null

    @Volatile
    private var running = false

    fun sendNextBatch() = lock.withLock {
        sendBatchCondition.signalAll()
    }

    private fun collectNextBatch(): Collection<Span> {
        val batchSize = synchronized(this) { batch.size }
        // only wait for a batch if the current batch is too small
        if (batchSize < InternalDebug.spanBatchSizeSendTriggerPoint) {
            lock.withLock {
                sendBatchCondition.await(
                    InternalDebug.spanBatchTimeoutMs,
                    TimeUnit.MILLISECONDS
                )
            }
        }

        synchronized(this) {
            val nextBatch = batch
            batch = ArrayList()
            return sampler.sampled(nextBatch)
        }
    }

    private fun addToBatch(span: Span) {
        val batchSize = synchronized(this) {
            batch.add(span)
            batch.size
        }

        if (batchSize >= InternalDebug.spanBatchSizeSendTriggerPoint) {
            sendNextBatch()
        }
    }

    override fun onEnd(span: Span) {
        if (sampler.shouldKeepSpan(span)) {
            addToBatch(span)
        }
    }

    override fun run() {
        delivery.fetchCurrentProbability { sampler.probability = it }
        while (running) {
            try {
                val nextBatch = collectNextBatch()
                Logger.d("Sending a batch of ${nextBatch.size} spans to $delivery")
                delivery.deliver(nextBatch, resourceAttributes) { sampler.probability = it }
            } catch (e: Exception) {
                Logger.e("Unexpected exception", e)
            }
        }
    }

    @Synchronized
    fun start(configuration: PerformanceConfiguration) {
        if (running) return
        running = true

        val delivery = RetryDelivery(
            InternalDebug.dropSpansOlderThanMs,
            HttpDelivery(
                configuration.endpoint,
                requireNotNull(configuration.apiKey) {
                    "PerformanceConfiguration.apiKey may not be null"
                }
            )
        )

        this.delivery = delivery
        this.serviceName = configuration.context.packageName
        this.resourceAttributes = createResourceAttributes(configuration)
        sampler.fallbackProbability = configuration.samplingProbability

        runner = Thread(this, "Bugsnag Tracer").apply {
            isDaemon = true
            start()
        }
    }

    fun stop(await: Boolean = true) {
        running = false
        runner?.interrupt()

        if (await) {
            runner?.join()
        }
    }
}
