package com.bugsnag.android.performance.internal.metrics

import com.bugsnag.android.performance.EnabledMetrics
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanMetrics
import com.bugsnag.android.performance.test.TestMetricsContainer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
internal class MetricsContainerDiskMetricsTest {
    private lateinit var diskMetrics: MetricSource<DiskIoSnapshot>
    private lateinit var metricsContainer: TestMetricsContainer

    @Before
    fun setup() {
        diskMetrics =
            object : MetricSource<DiskIoSnapshot> {
                override fun createStartMetrics(): DiskIoSnapshot {
                    return DiskIoSnapshot(
                        readSyscalls = 1L,
                        writeSyscalls = 1L,
                        timestampNanos = 1L,
                    )
                }

                override fun endMetrics(
                    startMetrics: DiskIoSnapshot,
                    span: Span,
                ) = Unit
            }

        metricsContainer =
            TestMetricsContainer(
                cpu = null,
                memory = null,
                frames = null,
                disk = diskMetrics,
            )
        metricsContainer.attach(RuntimeEnvironment.getApplication())
    }

    @Test
    fun attachInstallsDiskMetricSource() {
        assertSame(diskMetrics, metricsContainer.diskIoMetricSource)
    }

    @Test
    fun configureDisablesDiskMetricSourceWhenDiskIsFalse() {
        metricsContainer.configure(
            EnabledMetrics(
                rendering = false,
                cpu = false,
                memory = false,
                disk = false,
            ),
        )

        assertNull(metricsContainer.diskIoMetricSource)
    }

    @Test
    fun configureKeepsDiskMetricSourceWhenDiskIsTrue() {
        metricsContainer.configure(
            EnabledMetrics(
                rendering = false,
                cpu = false,
                memory = false,
                disk = true,
            ),
        )

        assertSame(diskMetrics, metricsContainer.diskIoMetricSource)
    }

    @Test
    fun createSpanMetricsSnapshotReturnsNullWhenDiskDisabledGlobally() {
        metricsContainer.configure(
            EnabledMetrics(
                rendering = false,
                cpu = false,
                memory = false,
                disk = false,
            ),
        )

        val snapshot =
            metricsContainer.createSpanMetricsSnapshot(
                defaultEnabled = true,
                spanMetrics = SpanMetrics(disk = true),
            )

        assertNull(snapshot)
    }

    @Test
    fun createSpanMetricsSnapshotIncludesDiskWhenEnabled() {
        metricsContainer.configure(
            EnabledMetrics(
                rendering = false,
                cpu = false,
                memory = false,
                disk = true,
            ),
        )

        val snapshot =
            metricsContainer.createSpanMetricsSnapshot(
                defaultEnabled = true,
                spanMetrics = SpanMetrics(disk = true),
            )

        assertNotNull(snapshot)
    }

    @Test
    fun createSpanMetricsSnapshotOmitsDiskWhenSpanMetricsDisablesIt() {
        metricsContainer.configure(
            EnabledMetrics(
                rendering = false,
                cpu = false,
                memory = false,
                disk = true,
            ),
        )

        val snapshot =
            metricsContainer.createSpanMetricsSnapshot(
                defaultEnabled = true,
                spanMetrics = SpanMetrics(disk = false),
            )

        assertNull(snapshot)
    }
}
