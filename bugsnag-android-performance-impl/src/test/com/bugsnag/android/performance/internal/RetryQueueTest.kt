package com.bugsnag.android.performance.internal

import android.os.SystemClock
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class RetryQueueTest {
    private lateinit var dir: File
    private lateinit var retryQueue: RetryQueue

    @Before
    fun createQueueDir() {
        dir =
            File(
                System.getProperty("java.io.tmpdir"),
                "retry-queue-test-${System.currentTimeMillis()}",
            )

        retryQueue = RetryQueue(dir)
    }

    @After
    fun deleteQueueDir() {
        dir.deleteRecursively()
    }

    @Test
    fun addPayloads() =
        withStaticMock<SystemClock> { mockedClock ->
            mockedClock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                .thenReturn(0L)

            val tracePayload1 =
                TracePayload(
                    1L,
                    byteArrayOf(1, 2, 3),
                    mapOf(
                        "Header1" to "one",
                        "Header2" to "two",
                    ),
                )

            val tracePayload2 =
                TracePayload(
                    2L,
                    byteArrayOf(3, 2, 1),
                    mapOf(
                        "One" to "1",
                        "Two" to "2",
                        "Three" to "3",
                    ),
                )

            retryQueue.add(tracePayload2)
            retryQueue.add(tracePayload1)

            val expectedPayload1 =
                tracePayload1.copy(timestamp = tracePayload1.timestamp + BugsnagClock.bootTimeNano)
            val expectedPayload2 =
                tracePayload2.copy(timestamp = tracePayload2.timestamp + BugsnagClock.bootTimeNano)

            assertEquals(expectedPayload2, retryQueue.next())
            retryQueue.remove(expectedPayload2.timestamp)
            assertEquals(expectedPayload1, retryQueue.next())
        }
}
