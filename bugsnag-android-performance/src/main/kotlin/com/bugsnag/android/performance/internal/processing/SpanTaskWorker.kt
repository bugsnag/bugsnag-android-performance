package com.bugsnag.android.performance.internal.processing

import android.os.SystemClock
import com.bugsnag.android.performance.Logger

import java.util.concurrent.DelayQueue
import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit

internal interface TimeoutExecutor {
    fun scheduleTimeout(timeout: Timeout)
    fun cancelTimeout(timeout: Timeout)
}

internal interface SamplerExecutor {
    fun addSampler(sampler: Runnable, sampleRateMs: Long = 1000L)
    fun removeSampler(sampler: Runnable)
}

internal interface ScheduledAction : Delayed, Runnable {
    override fun compareTo(other: Delayed): Int {
        val delay = getDelay(TimeUnit.NANOSECONDS)
        val otherDelay = other.getDelay(TimeUnit.NANOSECONDS)
        return when {
            delay < otherDelay -> -1
            delay > otherDelay -> 1
            else -> 0
        }
    }
}

internal interface Timeout : ScheduledAction {
    /**
     * The time that this `Timeout` is "due" relative to the [SystemClock.elapsedRealtime] clock.
     */
    val target: Long

    /**
     * Return the number of milliseconds before this Timeout has elapsed (may be `<=0` if the
     * timeout has already passed).
     */
    val relativeMs: Long
        get() = target - SystemClock.elapsedRealtime()

    override fun getDelay(unit: TimeUnit): Long {
        return unit.convert(relativeMs, TimeUnit.MILLISECONDS)
    }
}

internal class SpanTaskWorker : Runnable, TimeoutExecutor, SamplerExecutor {
    /*
     * The SpanTaskWorker is a Timer/ScheduledExecutor/HandlerThread style class, but allows tasks
     * to be added and removed before the backing thread is actually started. Once it has been
     * started any past tasks are executed immediately. This suits us nicely since our configuration
     * only typically settles some time after the first actions have already been taken.
     */

    @Volatile
    private var running = false

    private var thread: Thread? = null

    private val actions = DelayQueue<ScheduledAction>()

    @Synchronized
    fun start() {
        if (running) {
            return
        }

        running = true
        thread = Thread(this, "Bugsnag Span Task Worker")
        thread?.start()
    }

    @Synchronized
    fun stop() {
        if (!running) {
            return
        }

        running = false
        thread?.interrupt()
        thread = null
    }

    override fun run() {
        while (running) {
            try {
                actions.take().run()
            } catch (ie: InterruptedException) {
                // ignore and continue, the thread may have been stopped
            } catch (ex: Exception) {
                Logger.w("unhandled exception in timeout", ex)
            }
        }
    }

    override fun scheduleTimeout(timeout: Timeout) {
        actions.add(timeout)
    }

    override fun cancelTimeout(timeout: Timeout) {
        actions.remove(timeout)
    }

    override fun addSampler(sampler: Runnable, sampleRateMs: Long) {
        actions.add(Sampler(sampleRateMs, sampler))
    }

    override fun removeSampler(sampler: Runnable) {
        actions.removeAll { it is Sampler && it.sampler === sampler }
    }

    private inner class Sampler(
        val sampleRateMs: Long,
        val sampler: Runnable,
    ) : ScheduledAction {
        private val baseTime = SystemClock.elapsedRealtime()

        private var runCount = 1L

        override fun getDelay(unit: TimeUnit): Long {
            val currentTime = SystemClock.elapsedRealtime()
            val expectedNextTime = baseTime + (runCount * sampleRateMs)

            return unit.convert(expectedNextTime - currentTime, TimeUnit.MILLISECONDS)
        }

        override fun run() {
            try {
                sampler.run()
            } finally {
                runCount++
                actions.add(this)
            }
        }
    }
}
