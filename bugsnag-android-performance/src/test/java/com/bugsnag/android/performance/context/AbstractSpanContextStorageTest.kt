package com.bugsnag.android.performance.context

import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanContextStorage
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.test.CollectingSpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.util.UUID

abstract class AbstractSpanContextStorageTest<S : SpanContextStorage> {
    protected lateinit var storage: S
        private set
    protected lateinit var spanFactory: SpanFactory
        private set
    protected lateinit var spanProcessor: CollectingSpanProcessor
        private set

    protected abstract fun createStorage(): S

    @Before
    fun setup() {
        storage = createStorage()
        spanProcessor = CollectingSpanProcessor()
        spanFactory = SpanFactory(spanProcessor)
    }

    @Test
    fun smokeTest_emptyStorage() {
        assertNull(storage.currentContext)
        assertEquals(0, storage.currentStack.count())
    }

    @Test
    fun smokeTest_singleContext() {
        val context = TestSpanContext(1L, UUID.randomUUID())
        storage.attach(context)

        assertSame(context, storage.currentContext)
        assertEquals(1, storage.currentStack.count())
    }

    @Test
    fun smokeTest_attachAndDetach() {
        val context = TestSpanContext(1L, UUID.randomUUID())
        storage.attach(context)
        assertSame(context, storage.currentContext)

        storage.detach(context)
        assertNull(storage.currentContext)
        assertEquals(0, storage.currentStack.count())
    }

    @Test
    fun smokeTest_multipleContextsStack() {
        val context1 = TestSpanContext(1L, UUID.randomUUID())
        val context2 = TestSpanContext(2L, UUID.randomUUID())
        val context3 = TestSpanContext(3L, UUID.randomUUID())

        storage.attach(context1)
        storage.attach(context2)
        storage.attach(context3)

        // Most recent should be on top
        assertSame(context3, storage.currentContext)
        assertEquals(3, storage.currentStack.count())
    }

    @Test
    fun smokeTest_detachInOrder() {
        val context1 = TestSpanContext(1L, UUID.randomUUID())
        val context2 = TestSpanContext(2L, UUID.randomUUID())
        val context3 = TestSpanContext(3L, UUID.randomUUID())

        storage.attach(context1)
        storage.attach(context2)
        storage.attach(context3)

        storage.detach(context3)
        assertSame(context2, storage.currentContext)

        storage.detach(context2)
        assertSame(context1, storage.currentContext)

        storage.detach(context1)
        assertNull(storage.currentContext)
    }

    @Test
    fun smokeTest_clear() {
        val context1 = TestSpanContext(1L, UUID.randomUUID())
        val context2 = TestSpanContext(2L, UUID.randomUUID())

        storage.attach(context1)
        storage.attach(context2)

        storage.clear()
        assertNull(storage.currentContext)
        assertEquals(0, storage.currentStack.count())
    }

    protected data class TestSpanContext(
        override val spanId: Long,
        override val traceId: UUID,
    ) : SpanContext
}
