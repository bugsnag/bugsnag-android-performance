package com.bugsnag.android.performance.coroutines

import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.SpanProcessor
import com.bugsnag.android.performance.internal.context.ThreadLocalSpanContextStorage
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
class BugsnagPerformanceCoroutineTest {
    private val testProcessor = SpanProcessor { }
    private lateinit var spanFactory: SpanFactory

    @Before
    fun newSpanFactory() {
        spanFactory = SpanFactory(testProcessor)
    }

    @Test
    fun asCoroutineElement() {
        spanFactory.createCustomSpan("root span").use { rootSpan ->
            val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            runBlocking(singleThreadDispatcher) {
                assertSame(SpanContext.invalid, SpanContext.current)
            }
            runBlocking(singleThreadDispatcher + SpanContext.current.asCoroutineElement()) {
                assertSame(rootSpan, SpanContext.current)
            }
        }
    }

    @Test
    fun plusOperator() {
        spanFactory.createCustomSpan("root span").use { rootSpan ->
            val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            runBlocking(singleThreadDispatcher + SpanContext.current) {
                assertSame(rootSpan, SpanContext.current)
            }

            runBlocking(SpanContext.current + singleThreadDispatcher) {
                assertSame(rootSpan, SpanContext.current)
            }
        }
    }

    @Test
    fun bugsnagPerformanceScope() {
        spanFactory.createCustomSpan("root span").use { rootSpan ->
            val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            runBlocking(BugsnagPerformanceScope(singleThreadDispatcher).coroutineContext) {
                assertSame(rootSpan, SpanContext.current)
            }
        }
    }

    @Test
    fun coroutineStacks() {
        spanFactory.createCustomSpan("root span").use {
            val coroutineRootSpan = spanFactory.createCustomSpan("coroutine root span")
            val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            runBlocking(dispatcher + SpanContext.current) {
                // coroutine has its own context stack with the 'current' context at the root
                assertSame(coroutineRootSpan, SpanContext.current)
                assertEquals(1, currentContextStackSize())
                // coroutines sharing a thread should maintain their own separate context stacks
                val subCoroutineSpan = spanFactory.createCustomSpan("sub-coroutine span")
                launch(SpanContext.current.asCoroutineElement()) {
                    assertSame(subCoroutineSpan, SpanContext.current)
                    assertEquals(1, currentContextStackSize())
                    // start some spans in this coroutine context
                    val coroutineSpan1 = spanFactory.createCustomSpan("coroutine span 1")
                    val coroutineSpan2 = spanFactory.createCustomSpan("coroutine span 2")
                    // suspend the coroutine to allow the second coroutine to execute
                    delay(50L)
                    // ensure the context stack has been preserved
                    assertSame(coroutineSpan2, SpanContext.current)
                    coroutineSpan2.end()
                    assertSame(coroutineSpan1, SpanContext.current)
                    coroutineSpan1.end()
                    // subCoroutineSpan was ended in the second coroutine
                    assertSame(SpanContext.invalid, SpanContext.current)
                }
                // this coroutine runs while the first one is suspended
                launch(SpanContext.current.asCoroutineElement()) {
                    assertSame(subCoroutineSpan, SpanContext.current)
                    assertEquals(1, currentContextStackSize())
                    // start some spans in this coroutine context
                    val coroutineSpan3 = spanFactory.createCustomSpan("coroutine span 3")
                    val coroutineSpan4 = spanFactory.createCustomSpan("coroutine span 4")
                    assertSame(coroutineSpan4, SpanContext.current)
                    coroutineSpan4.end()
                    assertSame(coroutineSpan3, SpanContext.current)
                    coroutineSpan3.end()
                    assertSame(subCoroutineSpan, SpanContext.current)
                    subCoroutineSpan.end()
                    assertSame(SpanContext.invalid, SpanContext.current)
                }
            }
        }
    }

    private fun currentContextStackSize(): Int {
        val contextStorage = SpanContext.DEFAULT_STORAGE as? ThreadLocalSpanContextStorage
        return contextStorage?.contextStack?.size ?: 0
    }
}
