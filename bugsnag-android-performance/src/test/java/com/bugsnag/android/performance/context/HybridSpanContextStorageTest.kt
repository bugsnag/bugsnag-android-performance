package com.bugsnag.android.performance.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Executors

class HybridSpanContextStorageTest : AbstractSpanContextStorageTest<HybridSpanContextStorage>() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun createStorage(): HybridSpanContextStorage = HybridSpanContextStorage()

    @Test
    fun testThreadLocalTrace() {
        val root = TestSpanContext(1L, UUID.randomUUID())
        val isolated = TestSpanContext(2L, UUID.randomUUID())
        storage.attach(root)

        executor.submit {
            assertEquals(root, storage.currentContext)
            storage.startThreadLocalTrace()

            assertNull(storage.currentContext)
            storage.attach(isolated)
            assertEquals(isolated, storage.currentContext)
            storage.detach(isolated)
            assertNull(storage.currentContext)

            storage.endThreadLocalTrace()
            assertEquals(root, storage.currentContext)
        }.get()
    }
}
