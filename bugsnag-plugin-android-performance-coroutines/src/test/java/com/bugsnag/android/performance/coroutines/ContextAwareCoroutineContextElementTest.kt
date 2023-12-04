package com.bugsnag.android.performance.coroutines

import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.SpanImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
class ContextAwareCoroutineContextElementTest {

    private lateinit var collectedSpans: MutableList<SpanImpl>

    private lateinit var spanFactory: SpanFactory

    @Before
    fun createSpanFactory() {
        collectedSpans = Collections.synchronizedList(ArrayList())
        spanFactory = SpanFactory(spanProcessor = { collectedSpans.add(it as SpanImpl) })
    }

    @Test
    fun testForkJoinCoroutines() = runBlocking {
        val scope = BugsnagPerformanceScope(Dispatchers.Default)

        scope.async {
            val rootSpan = spanFactory.createCustomSpan("RootSpan")
            rootSpan.use {
                (1..10)
                    .map { jobId ->
                        async(Dispatchers.Default) {
                            spanFactory.createCustomSpan("Job$jobId").use {
                                delay(10L)
                                withContext(Dispatchers.IO) {
                                    spanFactory.createCustomSpan("Job$jobId").use {
                                        delay(10L)
                                    }
                                }
                            }

                            jobId
                        }
                    }
                    .awaitAll()
            }

            assertEquals(21, collectedSpans.size)
            assertTrue("RootSpan was not ended", collectedSpans.remove(rootSpan))

            collectedSpans.forEach { span ->
                assertEquals(
                    "all spans should belong to the same trace",
                    rootSpan.traceId,
                    span.traceId,
                )
            }

            val (directChildren, nestedChildren) = collectedSpans.partition { it.parentSpanId == rootSpan.spanId }

            assertEquals(10, directChildren.size)
            assertEquals(10, nestedChildren.size)

            val seenParentIds = mutableSetOf<Long>()

            // given that all of these were Forked (async) directly under the rootSpan we
            // expect all of them to have rootSpan as their logical parent
            nestedChildren.forEach { span ->
                assertNotNull(
                    "nested span has unknown parent: ${span.parentSpanId}",
                    directChildren.find { it.spanId == span.parentSpanId },
                )

                assertTrue(
                    "unexpected parentSpanId, this parent has more than one child",
                    seenParentIds.add(span.parentSpanId),
                )
            }
        }.await() // wait for the launched job to complete
    }
}
