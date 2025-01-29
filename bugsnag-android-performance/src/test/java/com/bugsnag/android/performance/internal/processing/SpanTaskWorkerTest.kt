package com.bugsnag.android.performance.internal.processing

import android.os.SystemClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class SpanTaskWorkerTest {

    private var worker: SpanTaskWorker? = null

    @Before
    fun setup() {
        worker = SpanTaskWorker()
    }

    @After
    fun tearDown() {
        worker?.stop()
        worker = null
    }

    @Test(timeout = 500L)
    fun populateBeforeStart() {
        val w = worker!!

        val latch = CountDownLatch(2)

        val t1 = TestTimeout(5L, latch)
        val t2 = TestTimeout(10L, latch)

        w.scheduleTimeout(t1)
        w.scheduleTimeout(t2)

        ShadowSystemClock.advanceBy(20L, TimeUnit.MILLISECONDS)

        assertEquals(0, t1.runCount)
        assertEquals(0, t2.runCount)

        w.start()
        latch.await()

        assertEquals(1, t1.runCount)
        assertEquals(1, t2.runCount)
    }

    @Test
    fun samplersRunAtFixedRate() {
        val w = worker!!
        var runCount = 0

        val sampler = Runnable {
            runCount++
        }

        w.addSampler(sampler, 2L)
        w.start()

        repeat(10) {
            ShadowSystemClock.advanceBy(1, TimeUnit.MILLISECONDS)
            Thread.sleep(1L)
        }

        assertEquals(5, runCount)
    }

    @Test
    fun errorTask() {
        val w = worker!!

        val latch = CountDownLatch(2)

        val t1 = object : TestTimeout(5L, latch) {
            override fun run() {
                super.run()
                throw IllegalStateException("oops")
            }
        }

        val t2 = TestTimeout(10L, latch)

        w.scheduleTimeout(t1)
        w.scheduleTimeout(t2)
        w.start()

        ShadowSystemClock.advanceBy(20L, TimeUnit.MILLISECONDS)
        latch.await()

        assertEquals(1, t1.runCount)
        assertEquals(1, t2.runCount)
    }

    private open class TestTimeout(
        timeoutMs: Long,
        private val latch: CountDownLatch? = null,
    ) : Timeout {
        var runCount = 0

        override val target: Long = timeoutMs + SystemClock.elapsedRealtime()

        override fun run() {
            runCount++
            latch?.countDown()
        }
    }
}
