package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanProcessor
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ArrayBlockingQueue

internal class Tracer : SpanProcessor, Runnable {
    private lateinit var delivery: Delivery
    private lateinit var serviceName: String
    private lateinit var resourceAttributes: Attributes

    internal val sampler = Sampler(1.0)
    private var batch = ArrayList<Span>()
    private val batchSendQueue = ArrayBlockingQueue<Collection<Span>>(batchSendQueueSize)
    private var batchTimeoutTask: TimerTask = object : TimerTask() {
        override fun run() {}
    }
    private val batchTimer = Timer()

    private var runner: Thread? = null

    @Volatile
    private var running = false

    private fun replaceBatchTimer() {
        batchTimeoutTask.cancel()
        val newTask = object : TimerTask() {
            override fun run() {
                sendNextBatch()
            }
        }
        batchTimer.schedule(newTask, InternalDebug.spanBatchTimeoutMs)
        batchTimeoutTask = newTask
    }

    fun sendNextBatch() {
        batchTimeoutTask.cancel()
        val nextBatch = collectNextBatch()
        // Send even if empty, to kick any retries
        batchSendQueue.add(nextBatch)
    }

    private fun collectNextBatch(): Collection<Span> {
        synchronized(this) {
            val nextBatch = batch
            batch = ArrayList()
            return sampler.sampled(nextBatch)
        }
    }

    private fun addToBatch(span: Span) {
        synchronized(this) {
            batch.add(span)
        }
        if (batch.size >= InternalDebug.spanBatchSizeSendTriggerPoint) {
            sendNextBatch()
        } else {
            replaceBatchTimer()
        }
    }

    override fun onEnd(span: Span) {
        if (sampler.shouldKeepSpan(span)) {
            addToBatch(span)
        }
    }

    override fun run() {
        while (running) {
            try {
                val nextBatch = batchSendQueue.take()
                delivery.deliver(nextBatch, resourceAttributes)
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
            HttpDelivery(configuration.endpoint)
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

    companion object {
        private const val batchSendQueueSize = 100
    }
}
