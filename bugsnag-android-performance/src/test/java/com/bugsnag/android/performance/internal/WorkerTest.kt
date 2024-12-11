package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Logger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch

class WorkerTest {
    @Before
    fun stubLogger() {
        Logger.delegate = NoopLogger
    }

    @After
    fun unStubLogger() {
        Logger.delegate = DebugLogger
    }

    @Test(timeout = 1000)
    fun testWaitAndWake() {
        val count1 = CountingTask(1)
        val count2 = CountingTask(5)
        val worker = Worker { listOf(count1, count2) }

        worker.start()
        try {
            count1.await()
            count2.await()
        } finally {
            worker.stop()
        }

        assertEquals(1, count1.currentCount)
        assertEquals(5, count2.currentCount)
    }

    @Test
    fun testWaitAndAwake() {
        val count1 = CountingTask(1)
        val count2 = CountingTask(5)
        val worker = Worker { listOf(count1, count2) }

        worker.start()
        try {
            count1.await()
            count2.await()

            assertEquals(1, count1.currentCount)
            assertEquals(5, count2.currentCount)

            count2.reset()

            // a short delay to ensure the worker has not woken up
            Thread.sleep(5L)

            // assert that the work was not done again
            assertEquals(0, count2.currentCount)

            worker.wake()
            count2.await()
            assertEquals(5, count2.currentCount)
        } finally {
            worker.stop()
        }
    }

    @Test
    fun testExceptionHandling() {
        val latch = CountDownLatch(1)
        val worker = Worker {
            listOf(
                object : Task {
                    override fun execute(): Boolean {
                        throw NullPointerException()
                    }
                },
                object : Task {
                    override fun execute(): Boolean {
                        latch.countDown()
                        return false
                    }
                },
            )
        }

        // this test succeeds if the latch releases, as it means that the exception in the first
        // task was correctly handled without blocking the second task
        worker.start()
        try {
            latch.await()
        } finally {
            worker.stop()
        }
    }

    private class CountingTask(
        private val maximum: Int,
    ) : Task {
        private var latch = CountDownLatch(maximum)

        var currentCount = 0
            private set

        fun await() = latch.await()

        fun reset() {
            currentCount = 0
            latch = CountDownLatch(maximum)
        }

        override fun execute(): Boolean {
            if (currentCount < maximum) {
                currentCount++
                latch.countDown()
                return true
            }

            return false
        }
    }
}
