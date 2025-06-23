package com.bugsnag.android.performance.internal.controls

import com.bugsnag.android.performance.controls.SpanControlProvider
import com.bugsnag.android.performance.controls.SpanQuery
import com.bugsnag.android.performance.internal.util.Prioritized
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class CompositeSpanControlProviderTest {
    @Test
    fun testNoProviders() {
        val compositeProvider = CompositeSpanControlProvider()
        assertNull(compositeProvider[DummySpanQuery(1)])
        assertNull(compositeProvider[UnsupportedSpanQuery])
    }

    @Test
    fun testCompositeSpanProvider() {
        val compositeSpanControlProvider =
            CompositeSpanControlProvider().apply {
                addProvider(Prioritized(1, DummySpanControlProvider(DummySpanQuery(1))))
                addProvider(Prioritized(1, DummySpanControlProvider(DummySpanQuery(2))))
                addProvider(Prioritized(1, DummySpanControlProvider(DummySpanQuery(3))))
            }

        assertEquals(
            DummySpanControl(1),
            compositeSpanControlProvider[DummySpanQuery(1)],
        )

        assertEquals(
            DummySpanControl(2),
            compositeSpanControlProvider[DummySpanQuery(2)],
        )

        assertEquals(
            DummySpanControl(3),
            compositeSpanControlProvider[DummySpanQuery(3)],
        )

        assertNull(compositeSpanControlProvider[DummySpanQuery(4)])
        assertNull(compositeSpanControlProvider[UnsupportedSpanQuery])
    }

    @Test
    fun testPrioritizedCompositeSpanProvider() {
        val compositeSpanControlProvider =
            CompositeSpanControlProvider().apply {
                addProvider(
                    Prioritized(
                        2,
                        DummySpanControlProvider(
                            DummySpanQuery(1),
                            DummySpanControl(987),
                        ),
                    ),
                )
                addProvider(Prioritized(1, DummySpanControlProvider(DummySpanQuery(1))))
                addProvider(Prioritized(1, DummySpanControlProvider(DummySpanQuery(2))))
            }

        assertEquals(DummySpanControl(987), compositeSpanControlProvider[DummySpanQuery(1)])
    }

    @Test
    fun testDuplicateProviders() {
        val provider1 = DummySpanControlProvider(DummySpanQuery(1))
        val provider2 = DummySpanControlProvider(DummySpanQuery(2))
        val provider3 = DummySpanControlProvider(DummySpanQuery(1))

        val compositeSpanControlProvider =
            CompositeSpanControlProvider().apply {
                addProvider(Prioritized(1, provider1))
                addProvider(Prioritized(1, provider2))
                addProvider(Prioritized(1, provider3))

                addProvider(Prioritized(2, provider1))
                addProvider(Prioritized(3, provider1))
                addProvider(Prioritized(4, provider1))
            }

        assertEquals(3, compositeSpanControlProvider.size)
    }

    @Test
    fun testAddMultipleProviders() {
        val provider1 = DummySpanControlProvider(DummySpanQuery(1))
        val provider2 = DummySpanControlProvider(DummySpanQuery(2))
        val provider3 = DummySpanControlProvider(DummySpanQuery(3))
        val provider4 = DummySpanControlProvider(DummySpanQuery(4))

        val compositeSpanControlProvider =
            CompositeSpanControlProvider().apply {
                addProvider(Prioritized(1, provider1))
                addProvider(Prioritized(1, provider2))

                addProviders(
                    listOf(
                        Prioritized(2, provider1),
                        Prioritized(3, provider2),
                        Prioritized(4, provider1),
                        Prioritized(4, provider3),
                    ),
                )

                addProvider(Prioritized(1, provider3))

                addProviders(
                    listOf(
                        Prioritized(4, provider4),
                    ),
                )
            }

        assertEquals(4, compositeSpanControlProvider.size)
        assertEquals(DummySpanControl(1), compositeSpanControlProvider[DummySpanQuery(1)])
        assertEquals(DummySpanControl(2), compositeSpanControlProvider[DummySpanQuery(2)])
        assertEquals(DummySpanControl(3), compositeSpanControlProvider[DummySpanQuery(3)])
        assertEquals(DummySpanControl(4), compositeSpanControlProvider[DummySpanQuery(4)])
        assertNull(compositeSpanControlProvider[UnsupportedSpanQuery])
    }

    data class DummySpanControl(val value: Int)

    data class DummySpanQuery(val value: Int) : SpanQuery<DummySpanControl>

    object UnsupportedSpanQuery : SpanQuery<String>

    class DummySpanControlProvider(
        private val expectedKey: DummySpanQuery,
        private val returnControls: DummySpanControl =
            DummySpanControl(
                expectedKey.value,
            ),
    ) : SpanControlProvider<DummySpanControl> {
        override operator fun <Q : SpanQuery<DummySpanControl>> get(query: Q): DummySpanControl? {
            if (query is DummySpanQuery && query == expectedKey) {
                return returnControls
            }

            return null
        }
    }
}
