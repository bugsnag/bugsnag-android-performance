package com.bugsnag.android.performance.internal.metrics

import android.os.SystemClock
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import java.io.File

/**
 * ROAD 2233 – Scenario 5 (Android / ED §3.1.4):
 * Disk metrics are omitted when the counter source is unavailable or unreadable.
 *
 * Maze cannot reliably force /proc/self/io failure modes on a real device, so these
 * cases are covered with injectable fixtures. iOS rows are out of scope here.
 */
@RunWith(Parameterized::class)
internal class DiskIoMetricsUnavailableSourceTest {
    companion object {
        private const val START_NS = 1_000_000_000L
        private const val END_NS = 3_000_000_000L

        @get:JvmStatic
        @get:Parameters(name = "{0}")
        val parameters =
            listOf(
                UnavailableSourceCase(
                    name = "proc_io_unreadable",
                    failureMode = FailureMode.UNREADABLE_PATH,
                ),
                UnavailableSourceCase(
                    name = "proc_io_missing_syscr",
                    failureMode = FailureMode.MISSING_SYSCR,
                ),
                UnavailableSourceCase(
                    name = "proc_io_non_numeric",
                    failureMode = FailureMode.NON_NUMERIC,
                ),
            )
    }

    @Parameter
    lateinit var testCase: UnavailableSourceCase

    private lateinit var ioFile: File

    @Before
    fun setup() {
        ioFile = File.createTempFile("diskio-unavailable", null)
    }

    @Test
    fun omitsDiskMetricsWhenSourceUnavailableAtStart() {
        val reader = readerForFailureAtStart()
        val source = DiskIoMetricsSource(reader)

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                .thenReturn(START_NS, START_NS, END_NS)

            val startSnapshot = source.createStartMetrics()
            // Even if a valid file appears later, an invalid start snapshot must omit metrics.
            writeValidIoFile(syscr = 200L, syscw = 100L)
            // Point reader at the valid file for end (only matters for path-based cases that reuse file).
            val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            source.endMetrics(startSnapshot, span)
            span.end()

            assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
            assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
            assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])
        }
    }

    @Test
    fun omitsDiskMetricsWhenSourceUnavailableAtEnd() {
        writeValidIoFile(syscr = 100L, syscw = 50L)
        val source = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                .thenReturn(START_NS, START_NS, END_NS)

            val startSnapshot = source.createStartMetrics()
            applyFailureContentForEnd()

            val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            source.endMetrics(startSnapshot, span)
            span.end()

            assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
            assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
            assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])
        }
    }

    @Test
    fun procIoReaderReportsFailureForMode() {
        val reader = readerForFailureAtStart()
        assertFalse(reader.parse(ProcIoReader.IoCounters()))
    }

    private fun readerForFailureAtStart(): ProcIoReader {
        return when (testCase.failureMode) {
            FailureMode.UNREADABLE_PATH ->
                ProcIoReader("/proc/does-not-exist-${System.nanoTime()}")
            FailureMode.MISSING_SYSCR -> {
                copyResourceToFile("io_missing_syscr")
                ProcIoReader(ioFile.absolutePath)
            }
            FailureMode.NON_NUMERIC -> {
                copyResourceToFile("io_non_numeric")
                ProcIoReader(ioFile.absolutePath)
            }
        }
    }

    private fun applyFailureContentForEnd() {
        when (testCase.failureMode) {
            FailureMode.UNREADABLE_PATH -> ioFile.delete()
            FailureMode.MISSING_SYSCR -> copyResourceToFile("io_missing_syscr")
            FailureMode.NON_NUMERIC -> copyResourceToFile("io_non_numeric")
        }
    }

    private fun writeValidIoFile(
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

    private fun copyResourceToFile(resourceName: String) {
        ioFile.outputStream().buffered().use { out ->
            requireNotNull(this::class.java.getResourceAsStream("/io/$resourceName")) {
                "cannot open $resourceName"
            }.use { it.copyTo(out) }
        }
    }

    internal enum class FailureMode {
        UNREADABLE_PATH,
        MISSING_SYSCR,
        NON_NUMERIC,
    }

    internal data class UnavailableSourceCase(
        val name: String,
        val failureMode: FailureMode,
    ) {
        override fun toString(): String = name
    }
}
