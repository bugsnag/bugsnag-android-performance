package com.bugsnag.android.performance.internal

import android.util.Log
import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanProcessor
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ArrayBlockingQueue

internal class Tracer : SpanProcessor, Runnable {
    private lateinit var delivery: Delivery
    private lateinit var serviceName: String

    private val resourceAttributes = Attributes()
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
            return nextBatch
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
        addToBatch(span)
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
    fun start(delivery: Delivery, serviceName: String) {
        if (running) return
        running = true

        this.delivery = delivery
        this.serviceName = serviceName

        resourceAttributes["service.name"] = serviceName
        resourceAttributes["telemetry.sdk.name"] = "bugsnag.performance.android"
        resourceAttributes["telemetry.sdk.version"] = "0.0.0"

        runner = Thread(this, "Bugsnag Tracer").apply {
            isDaemon = true
            start()
        }
    }

    companion object {
        private const val batchSendQueueSize = 100
    }
}
