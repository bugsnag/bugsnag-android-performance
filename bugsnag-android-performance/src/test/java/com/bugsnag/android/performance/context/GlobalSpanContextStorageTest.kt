package com.bugsnag.android.performance.context

import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.test.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class GlobalSpanContextStorageTest : AbstractSpanContextStorageTest<GlobalSpanContextStorage>() {
    override fun createStorage(): GlobalSpanContextStorage = GlobalSpanContextStorage()

    @Test
    fun detachOutOfOrder_doesNotPop() {
        val context1 = TestSpanContext(1L, UUID.randomUUID())
        val context2 = TestSpanContext(2L, UUID.randomUUID())
        val context3 = TestSpanContext(3L, UUID.randomUUID())

        storage.attach(context1)
        storage.attach(context2)
        storage.attach(context3)

        // Try to detach context2 when context3 is on top - should not pop
        storage.detach(context2)
        assertSame(context3, storage.currentContext)
        assertEquals(3, storage.currentStack.count())
    }

    @Test
    fun detachNonExistent_noEffect() {
        val context1 = TestSpanContext(1L, UUID.randomUUID())
        val context2 = TestSpanContext(2L, UUID.randomUUID())

        storage.attach(context1)

        // Try to detach a context that was never attached
        storage.detach(context2)
        assertSame(context1, storage.currentContext)
    }

    @Test
    fun currentContextSkipsEndedSpans() {
        val span1 = createTestSpan("span1")
        val span2 = createTestSpan("span2")

        storage.attach(span1)
        storage.attach(span2)

        // End span2 (top of stack)
        span2.end()

        // currentContext should pop the ended span2 and return span1
        assertSame(span1, storage.currentContext)

        val stackList = storage.currentStack.toList()
        assertEquals(listOf(span1), stackList)
    }

    @Test
    fun currentStackReturnsAllContexts() {
        val context1 = TestSpanContext(1L, UUID.randomUUID())
        val context2 = TestSpanContext(2L, UUID.randomUUID())
        val context3 = TestSpanContext(3L, UUID.randomUUID())

        storage.attach(context1)
        storage.attach(context2)
        storage.attach(context3)

        val stackList = storage.currentStack.toList()
        assertEquals(3, stackList.size)
        assertSame(context3, stackList[0])
        assertSame(context2, stackList[1])
        assertSame(context1, stackList[2])
    }

    @Test
    fun multiThreadedStressTest_globalBehavior() {
        val threadsCount = 10
        val iterationsPerThread = 1000
        val errors = AtomicInteger(0)
        val startLatch = CountDownLatch(1)
        val completionLatch = CountDownLatch(threadsCount)

        val tasks =
            (0 until threadsCount).map { threadIndex ->
                task {
                    try {
                        // Wait for all threads to be ready
                        startLatch.await()

                        repeat(iterationsPerThread) { iteration ->
                            val context =
                                TestSpanContext(
                                    (threadIndex * iterationsPerThread + iteration).toLong(),
                                    UUID.randomUUID(),
                                )
                            storage.attach(context)

                            // Verify we can get some current context (may not be ours in global mode)
                            val current = storage.currentContext
                            if (current == null) {
                                errors.incrementAndGet()
                            }

                            // Detach our context
                            storage.detach(context)
                        }
                    } catch (e: Exception) {
                        errors.incrementAndGet()
                        throw e
                    } finally {
                        completionLatch.countDown()
                    }
                }
            }

        // Start all threads at once
        startLatch.countDown()

        // Wait for all to complete
        completionLatch.await()

        // Get any exceptions
        tasks.forEach { it.get() }

        // All contexts should have been detached
        assertEquals(0, errors.get())
    }

    @Test
    fun multiThreadedStressTest_concurrentAttachDetach() {
        val threadsCount = 8
        val operationsPerThread = 500
        val startLatch = CountDownLatch(1)
        val completionLatch = CountDownLatch(threadsCount)

        val tasks =
            (0 until threadsCount).map { threadIndex ->
                task {
                    try {
                        startLatch.await()

                        val contexts = mutableListOf<TestSpanContext>()
                        repeat(operationsPerThread) { iteration ->
                            // Attach a new context
                            val context =
                                TestSpanContext(
                                    (threadIndex * operationsPerThread + iteration).toLong(),
                                    UUID.randomUUID(),
                                )
                            storage.attach(context)
                            contexts.add(context)

                            // Occasionally detach in reverse order
                            if (contexts.size > 5 && iteration % 10 == 0) {
                                repeat(3) {
                                    if (contexts.isNotEmpty()) {
                                        storage.detach(contexts.removeLast())
                                    }
                                }
                            }
                        }

                        // Clean up remaining contexts
                        contexts.reversed().forEach { storage.detach(it) }
                    } finally {
                        completionLatch.countDown()
                    }
                }
            }

        startLatch.countDown()
        // wait for all the threads to complete
        completionLatch.await()
        tasks.forEach { it.get() }

        // attempt to force a GC, which should release all of the spans
        System.gc()

        val remainingStackSize = storage.currentStack.count()
        assertEquals(0, remainingStackSize)
    }

    @Test
    fun multiThreadedStressTest_withRealSpans() {
        val threadsCount = 6
        val spansPerThread = 100
        val startLatch = CountDownLatch(1)
        val completionLatch = CountDownLatch(threadsCount)

        val tasks =
            (0 until threadsCount).map { threadIndex ->
                task {
                    try {
                        startLatch.await()

                        repeat(spansPerThread) { iteration ->
                            val span = createTestSpan("thread-$threadIndex-span-$iteration")
                            storage.attach(span)

                            // Do some "work"
                            Thread.sleep(1)

                            span.end()
                            storage.detach(span)
                        }
                    } finally {
                        completionLatch.countDown()
                    }
                }
            }

        startLatch.countDown()
        completionLatch.await()
        tasks.forEach { it.get() }

        // Verify all spans were collected
        val collectedSpans = spanProcessor.toList()
        assertEquals(threadsCount * spansPerThread, collectedSpans.size)
    }

    @Test
    fun runnableWrapper() {
        val context = TestSpanContext(1L, UUID.randomUUID())
        storage.attach(context)

        val executorService = Executors.newSingleThreadExecutor()
        var capturedContext: SpanContext? = null

        executorService.submit(
            context.wrap {
                capturedContext = storage.currentContext
            },
        ).get()

        executorService.shutdown()

        // In global storage, the context propagation happens through the global stack
        // The wrapped runnable doesn't change thread-local state
        assertSame(context, capturedContext)
    }

    @Test
    fun callableWrapper() {
        val context = TestSpanContext(1L, UUID.randomUUID())
        storage.attach(context)

        val executorService = Executors.newSingleThreadExecutor()
        val result =
            executorService.submit(
                context.wrap(
                    Callable {
                        storage.currentContext
                    },
                ),
            ).get()

        executorService.shutdown()

        assertSame(context, result)
    }

    @Test
    fun detachWithEndedSpan_stillPops() {
        val span1 = createTestSpan("span1")
        val span2 = createTestSpan("span2")

        storage.attach(span1)
        storage.attach(span2)

        // End span2 and then detach it
        span2.end()
        storage.detach(span2)

        // Should pop the ended span and reveal span1
        assertSame(span1, storage.currentContext)
    }

    private fun createTestSpan(
        name: String = "Test/test span",
        options: SpanOptions = SpanOptions.DEFAULTS,
    ) = spanFactory.createCustomSpan(name, options)
}
