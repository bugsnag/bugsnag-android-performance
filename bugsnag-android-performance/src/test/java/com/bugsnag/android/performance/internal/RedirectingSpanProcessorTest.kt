package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.test.CollectingSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.concurrent.thread

class RedirectingSpanProcessorTest {
    private val spanThreads = 10
    private val spansPerThread = 100

    @Test
    fun multithreadedRedirect() {
        val collectingSpanProcessor = CollectingSpanProcessor()
        val redirectingSpanProcessor = RedirectingSpanProcessor()

        val generatorThreads = (1..spanThreads).map { threadIndex ->
            thread(name = "Generator $threadIndex") {
                val factory = TestSpanFactory("Test/Thread($threadIndex)-%d")
                repeat(spansPerThread) {
                    factory.newSpan(endTime = { it + 1L }, processor = redirectingSpanProcessor)
                    Thread.sleep(1L)
                }
            }
        }

        // we inject the redirect after a few milliseconds - just long enough to cause both
        // batching and full-redirect behaviours to be required to finish the test
        Thread.sleep(10L)
        redirectingSpanProcessor.redirectTo(collectingSpanProcessor)

        // wait for all the threads to be finished
        generatorThreads.forEach { it.join() }

        // we only assert that the correct number of spans was found
        val spansLogged = collectingSpanProcessor.toList()
        assertEquals(spanThreads * spansPerThread, spansLogged.size)
    }
}
