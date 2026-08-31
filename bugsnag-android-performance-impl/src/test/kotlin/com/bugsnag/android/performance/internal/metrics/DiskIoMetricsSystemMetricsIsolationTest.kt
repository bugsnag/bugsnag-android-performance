package com.bugsnag.android.performance.internal.metrics

import android.os.SystemClock
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.framerate.FramerateMetricsSnapshot
import com.bugsnag.android.performance.internal.framerate.TimestampPairBuffer
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import java.io.File

/**
 * ROAD 2233 – Scenario 13 (Android):
 * Existing CPU, memory, and frozen-frame metrics are unaffected by disk IOPS collection.
 */
@RunWith(Parameterized::class)
internal class DiskIoMetricsSystemMetricsIsolationTest {
    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val ATTR_CPU_MEAN = "bugsnag.system.cpu_mean_total"
        private const val ATTR_MEMORY_MEAN = "bugsnag.system.memory.spaces.device.mean"
        private const val ATTR_FROZEN_FRAMES = "bugsnag.rendering.frozen_frames"

        private const val EXPECTED_CPU_MEAN = 42.0
        private const val EXPECTED_MEMORY_MEAN = 128.0
        private const val EXPECTED_FROZEN_FRAMES = 3L

        @get:JvmStatic
        @get:Parameters(name = "{0}")
        val parameters =
            listOf(
                SystemMetricsDiskState(
                    name = "enabled_with_valid_disk_data",
                    diskMode = DiskMode.ENABLED_VALID,
                ),
                SystemMetricsDiskState(
                    name = "disabled_source_unavailable",
                    diskMode = DiskMode.DISABLED,
                ),
                SystemMetricsDiskState(
                    name = "emitting_zero_iops_values",
                    diskMode = DiskMode.ZERO_IOPS,
                ),
            )
    }

    @Parameter
    lateinit var testCase: SystemMetricsDiskState

    private lateinit var ioFile: File

    @Before
    fun setup() {
        ioFile = File.createTempFile("diskio-system-metrics", null)
    }

    @Test
    fun systemMetricsUnaffectedByDiskIopsCollection() {
        val cpuSource = stubCpuSource()
        val memorySource = stubMemorySource()
        val renderingSource = stubRenderingSource()

        val span =
            when (testCase.diskMode) {
                DiskMode.ENABLED_VALID ->
                    finishSpanWithDisk(
                        cpuSource,
                        memorySource,
                        renderingSource,
                        readStart = 100L,
                        writeStart = 50L,
                        readEnd = 200L,
                        writeEnd = 100L,
                    )
                DiskMode.ZERO_IOPS ->
                    finishSpanWithDisk(
                        cpuSource,
                        memorySource,
                        renderingSource,
                        readStart = 500L,
                        writeStart = 250L,
                        readEnd = 500L,
                        writeEnd = 250L,
                    )
                DiskMode.DISABLED -> finishSpanWithoutDisk(cpuSource, memorySource, renderingSource)
            }

        assertSystemMetricsPresent(span)

        when (testCase.diskMode) {
            DiskMode.ENABLED_VALID -> {
                assertEquals(50.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
                assertEquals(25.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
                assertEquals(75.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])
            }
            DiskMode.ZERO_IOPS -> {
                assertEquals(0.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
                assertEquals(0.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
                assertEquals(0.0, span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])
            }
            DiskMode.DISABLED -> {
                assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
                assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
                assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])
            }
        }
    }

    private fun finishSpanWithDisk(
        cpuSource: SampledMetricSource<CpuMetricsSnapshot>,
        memorySource: SampledMetricSource<MemoryMetricsSnapshot>,
        renderingSource: MetricSource<FramerateMetricsSnapshot>,
        readStart: Long,
        writeStart: Long,
        readEnd: Long,
        writeEnd: Long,
    ): SpanImpl {
        writeIoFile(syscr = readStart, syscw = writeStart)

        lateinit var span: SpanImpl
        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                .thenReturn(NANOS_PER_SECOND, NANOS_PER_SECOND, 3L * NANOS_PER_SECOND)

            val diskSource = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
            val snapshot =
                SpanMetricsSnapshot.createIfRequired(
                    renderingMetricsSource = renderingSource,
                    cpuMetricsSource = cpuSource,
                    memoryMetricsSource = memorySource,
                    diskIoMetricsSource = diskSource,
                )
            assertNotNull(snapshot)

            writeIoFile(syscr = readEnd, syscw = writeEnd)
            span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            snapshot!!.finish(span)
        }
        return span
    }

    private fun finishSpanWithoutDisk(
        cpuSource: SampledMetricSource<CpuMetricsSnapshot>,
        memorySource: SampledMetricSource<MemoryMetricsSnapshot>,
        renderingSource: MetricSource<FramerateMetricsSnapshot>,
    ): SpanImpl {
        val snapshot =
            SpanMetricsSnapshot.createIfRequired(
                renderingMetricsSource = renderingSource,
                cpuMetricsSource = cpuSource,
                memoryMetricsSource = memorySource,
                diskIoMetricsSource = null,
            )
        assertNotNull(snapshot)

        val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
        snapshot!!.finish(span)
        return span
    }

    private fun assertSystemMetricsPresent(span: SpanImpl) {
        assertEquals(EXPECTED_CPU_MEAN, span.attributes[ATTR_CPU_MEAN])
        assertEquals(EXPECTED_MEMORY_MEAN, span.attributes[ATTR_MEMORY_MEAN])
        assertEquals(EXPECTED_FROZEN_FRAMES, span.attributes[ATTR_FROZEN_FRAMES])
    }

    private fun stubCpuSource(): SampledMetricSource<CpuMetricsSnapshot> =
        object : SampledMetricSource<CpuMetricsSnapshot> {
            override fun run() = Unit

            override fun createStartMetrics(): CpuMetricsSnapshot = CpuMetricsSnapshot(0)

            override fun endMetrics(
                startMetrics: CpuMetricsSnapshot,
                span: Span,
            ) {
                (span as SpanImpl).attributes[ATTR_CPU_MEAN] = EXPECTED_CPU_MEAN
            }
        }

    private fun stubMemorySource(): SampledMetricSource<MemoryMetricsSnapshot> =
        object : SampledMetricSource<MemoryMetricsSnapshot> {
            override fun run() = Unit

            override fun createStartMetrics(): MemoryMetricsSnapshot = MemoryMetricsSnapshot(0)

            override fun endMetrics(
                startMetrics: MemoryMetricsSnapshot,
                span: Span,
            ) {
                (span as SpanImpl).attributes[ATTR_MEMORY_MEAN] = EXPECTED_MEMORY_MEAN
            }
        }

    private fun stubRenderingSource(): MetricSource<FramerateMetricsSnapshot> =
        object : MetricSource<FramerateMetricsSnapshot> {
            private val buffer = TimestampPairBuffer()

            override fun createStartMetrics(): FramerateMetricsSnapshot =
                FramerateMetricsSnapshot(0L, 0L, 0L, buffer, 0)

            override fun endMetrics(
                startMetrics: FramerateMetricsSnapshot,
                span: Span,
            ) {
                (span as SpanImpl).attributes[ATTR_FROZEN_FRAMES] = EXPECTED_FROZEN_FRAMES
            }
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

    internal enum class DiskMode {
        ENABLED_VALID,
        DISABLED,
        ZERO_IOPS,
    }

    internal data class SystemMetricsDiskState(
        val name: String,
        val diskMode: DiskMode,
    ) {
        override fun toString(): String = name
    }
}
