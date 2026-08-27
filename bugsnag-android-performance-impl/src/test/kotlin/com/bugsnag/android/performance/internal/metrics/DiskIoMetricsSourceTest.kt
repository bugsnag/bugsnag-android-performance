package com.bugsnag.android.performance.internal.metrics

import android.os.SystemClock
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.File

internal class DiskIoMetricsSourceTest {
    private lateinit var ioFile: File

    @Before
    fun setup() {
        ioFile = File.createTempFile("disk-io", null)
    }

    @Test
    fun createStartMetricsReturnsSnapshotOnSuccess() {
        writeIoFile(syscr = 100L, syscw = 50L)

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos).thenReturn(123L)

            val source = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
            val snapshot = source.createStartMetrics()

            assertEquals(100L, snapshot.readSyscalls)
            assertEquals(50L, snapshot.writeSyscalls)
            assertEquals(123L, snapshot.timestampNanos)
        }
    }

    @Test
    fun createStartMetricsReturnsInvalidSnapshotOnReadFailure() {
        val source = DiskIoMetricsSource(ProcIoReader("/proc/does-not-exist-${System.nanoTime()}"))
        val snapshot = source.createStartMetrics()

        assertEquals(-1L, snapshot.readSyscalls)
        assertEquals(-1L, snapshot.writeSyscalls)
        assertEquals(0L, snapshot.timestampNanos)
    }

    @Test
    fun endMetricsSetsIopsAttributesOnSpan() {
        writeIoFile(syscr = 100L, syscw = 50L)

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                .thenReturn(0L, 2_000_000_000L)

            val source = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
            val startSnapshot = source.createStartMetrics()
            writeIoFile(syscr = 200L, syscw = 100L)

            val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            source.endMetrics(startSnapshot, span)

            assertEquals(50.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
            assertEquals(25.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
            assertEquals(75.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])
        }
    }

    @Test
    fun endMetricsSetsZeroIopsWhenCountersUnchanged() {
        writeIoFile(syscr = 500L, syscw = 250L)

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                .thenReturn(1_000_000_000L, 3_000_000_000L)

            val source = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
            val startSnapshot = source.createStartMetrics()

            val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            source.endMetrics(startSnapshot, span)

            assertEquals(0.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
            assertEquals(0.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
            assertEquals(0.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])
        }
    }

    @Test
    fun endMetricsIgnoresInvalidStartSnapshot() {
        val source = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
        val invalidSnapshot =
            DiskIoSnapshot(
                readSyscalls = -1L,
                writeSyscalls = 50L,
                timestampNanos = 100L,
            )
        val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)

        source.endMetrics(invalidSnapshot, span)

        assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
        assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
        assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])
    }

    @Test
    fun endMetricsIgnoresStartSnapshotWithZeroTimestamp() {
        val source = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
        val invalidSnapshot =
            DiskIoSnapshot(
                readSyscalls = 100L,
                writeSyscalls = 50L,
                timestampNanos = 0L,
            )
        writeIoFile(syscr = 200L, syscw = 100L)

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos).thenReturn(1_000_000_000L)

            val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            source.endMetrics(invalidSnapshot, span)

            assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
        }
    }

    @Test
    fun endMetricsCalculatesReadIopsOnlyWhenWriteUnchanged() {
        writeIoFile(syscr = 100L, syscw = 50L)

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                .thenReturn(0L, 4_000_000_000L)

            val source = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
            val startSnapshot = source.createStartMetrics()
            writeIoFile(syscr = 300L, syscw = 50L)

            val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            source.endMetrics(startSnapshot, span)

            assertEquals(50.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
            assertEquals(0.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
            assertEquals(50.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])
        }
    }

    @Test
    fun endMetricsIgnoresWhenEndReadFails() {
        writeIoFile(syscr = 100L, syscw = 50L)

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos).thenReturn(0L, 1_000_000_000L)

            val source = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
            val startSnapshot = source.createStartMetrics()
            ioFile.delete()

            val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            source.endMetrics(startSnapshot, span)

            assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
        }
    }

    @Test
    fun endMetricsIgnoresNonPositiveDuration() {
        val source = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
        val startSnapshot =
            DiskIoSnapshot(
                readSyscalls = 100L,
                writeSyscalls = 50L,
                timestampNanos = 5_000_000_000L,
            )
        writeIoFile(syscr = 200L, syscw = 100L)

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos).thenReturn(5_000_000_000L)

            val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            source.endMetrics(startSnapshot, span)

            assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
        }
    }

    @Test
    fun endMetricsIgnoresNegativeReadDelta() {
        val source = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
        val startSnapshot =
            DiskIoSnapshot(
                readSyscalls = 200L,
                writeSyscalls = 50L,
                timestampNanos = 0L,
            )
        writeIoFile(syscr = 100L, syscw = 50L)

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos).thenReturn(1_000_000_000L)

            val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            source.endMetrics(startSnapshot, span)

            assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
        }
    }

    @Test
    fun endMetricsIgnoresNegativeWriteDelta() {
        val source = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
        val startSnapshot =
            DiskIoSnapshot(
                readSyscalls = 100L,
                writeSyscalls = 200L,
                timestampNanos = 0L,
            )
        writeIoFile(syscr = 150L, syscw = 100L)

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos).thenReturn(1_000_000_000L)

            val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            source.endMetrics(startSnapshot, span)

            assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
        }
    }

    @Test
    fun endMetricsIgnoresNonSpanImpl() {
        writeIoFile(syscr = 100L, syscw = 50L)

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                .thenReturn(0L, 1_000_000_000L)

            val source = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
            val startSnapshot = source.createStartMetrics()
            writeIoFile(syscr = 200L, syscw = 100L)

            val span = mock<Span>()
            source.endMetrics(startSnapshot, span)
        }
    }

    @Test
    fun spanMetricsSnapshotFinishAppliesDiskIoMetrics() {
        writeIoFile(syscr = 10L, syscw = 5L)

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                .thenReturn(0L, 1_000_000_000L)

            val diskSource = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
            val snapshot =
                SpanMetricsSnapshot.createIfRequired(
                    renderingMetricsSource = null,
                    cpuMetricsSource = null,
                    memoryMetricsSource = null,
                    diskIoMetricsSource = diskSource,
                )

            assertTrue(snapshot != null)
            writeIoFile(syscr = 30L, syscw = 15L)

            val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            snapshot!!.finish(span)

            assertEquals(20.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
            assertEquals(10.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
            assertEquals(30.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])
        }
    }

    @Test
    fun spanMetricsSnapshotCreateIfRequiredReturnsNullWithoutDiskSource() {
        val snapshot =
            SpanMetricsSnapshot.createIfRequired(
                renderingMetricsSource = null,
                cpuMetricsSource = null,
                memoryMetricsSource = null,
                diskIoMetricsSource = null,
            )

        assertNull(snapshot)
    }

    private fun writeIoFile(
        syscr: Long,
        syscw: Long,
    ) {
        ioFile.writeText(
            """
            rchar: 0
            wchar: 0
            syscr: $syscr
            syscw: $syscw
            read_bytes: 0
            write_bytes: 0
            cancelled_write_bytes: 0
            """.trimIndent(),
        )
    }
}
