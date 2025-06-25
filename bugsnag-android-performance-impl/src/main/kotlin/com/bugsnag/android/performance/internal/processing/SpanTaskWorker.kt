package com.bugsnag.android.performance.internal.processing

import android.os.SystemClock
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.Logger
import java.util.concurrent.DelayQueue
import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface TimeoutExecutor {
    public fun scheduleTimeout(timeout: Timeout)

    public fun cancelTimeout(timeout: Timeout)
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface SamplerExecutor {
    public fun addSampler(
        sampler: Runnable,
        sampleRateMs: Long = 1000L,
    )

    public fun removeSampler(sampler: Runnable)
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface ScheduledAction : Delayed, Runnable {
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

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface Timeout : ScheduledAction {
    /**
     * The time that this `Timeout` is "due" relative to the [SystemClock.elapsedRealtime] clock.
     */
    public val target: Long

    /**
     * Return the number of milliseconds before this Timeout has elapsed (may be `<=0` if the
     * timeout has already passed).
     */
    public val relativeMs: Long
        get() = target - SystemClock.elapsedRealtime()

    override fun getDelay(unit: TimeUnit): Long {
        return unit.convert(relativeMs, TimeUnit.MILLISECONDS)
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SpanTaskWorker : Runnable, TimeoutExecutor, SamplerExecutor {
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
    public fun start() {
        if (running) {
            return
        }

        running = true
        thread = Thread(this, "Bugsnag Span Task Worker")
        thread?.start()
    }

    @Synchronized
    public fun stop() {
        if (!running) {
            return
        }

        running = false
        thread?.interrupt()
        thread = null
    }

    override fun run() {
        while (running) {
            runTask(actions.take())
        }
    }

    override fun scheduleTimeout(timeout: Timeout) {
        actions.add(timeout)
    }

    override fun cancelTimeout(timeout: Timeout) {
        actions.remove(timeout)
    }

    override fun addSampler(
        sampler: Runnable,
        sampleRateMs: Long,
    ) {
        actions.add(Sampler(sampler, sampleRateMs))
    }

    override fun removeSampler(sampler: Runnable) {
        actions.removeAll { it is Sampler && it.sampler === sampler }
    }

    private fun runTask(task: Runnable) {
        try {
            task.run()
        } catch (ie: InterruptedException) {
            // ignore and continue, the thread may have been stopped
        } catch (ex: Exception) {
            Logger.w("unhandled exception in span task: $task", ex)
        }
    }

    private inner class Sampler(
        val sampler: Runnable,
        val sampleRateMs: Long,
    ) : ScheduledAction {
        private var baseTime: Long = 0L

        private var runCount = 0L

        override fun getDelay(unit: TimeUnit): Long {
            if (runCount == 0L) {
                return 0L
            }

            val currentTime = SystemClock.elapsedRealtime()
            val expectedNextTime = baseTime + (runCount * sampleRateMs)

            return unit.convert(expectedNextTime - currentTime, TimeUnit.MILLISECONDS)
        }

        override fun run() {
            try {
                // the "baseTime" is the first time the sampler is run
                if (runCount == 0L) {
                    baseTime = SystemClock.elapsedRealtime()
                }

                sampler.run()
            } finally {
                runCount++
                actions.add(this)
            }
        }
    }
}
