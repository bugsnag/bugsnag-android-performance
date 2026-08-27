package com.bugsnag.android.performance.internal.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import java.io.File

@RunWith(Parameterized::class)
internal class ProcIoReaderTest {
    companion object {
        @get:JvmStatic
        @get:Parameters(name = "{0}")
        val parameters =
            listOf(
                Triple("io1", 1000L, 500L),
                Triple("io2", 42L, 17L),
                Triple("io3", 888L, 999L),
            )
    }

    @Parameter
    lateinit var testData: Triple<String, Long, Long>

    lateinit var file: File

    @Before
    fun setup() {
        val (ioFileName) = testData
        file = File.createTempFile("iotest", null)
        file.outputStream().buffered().use { out ->
            requireNotNull(this::class.java.getResourceAsStream("/io/$ioFileName")) {
                "cannot open $ioFileName"
            }.use { it.copyTo(out) }
        }
    }

    @Test
    fun parseValidIoFile() {
        val (_, expectedReadSyscalls, expectedWriteSyscalls) = testData
        val parser = ProcIoReader(file.absolutePath)
        val output = ProcIoReader.IoCounters()
        assertTrue(parser.parse(output))
        assertEquals(expectedReadSyscalls, output.readSyscalls)
        assertEquals(expectedWriteSyscalls, output.writeSyscalls)
        assertEquals(expectedReadSyscalls + expectedWriteSyscalls, output.totalSyscalls)
    }

    @Test
    fun parseReusesTargetWithoutStaleValues() {
        val (_, expectedReadSyscalls, expectedWriteSyscalls) = testData
        val parser = ProcIoReader(file.absolutePath)
        val output = ProcIoReader.IoCounters(readSyscalls = 99L, writeSyscalls = 99L)
        assertTrue(parser.parse(output))
        assertEquals(expectedReadSyscalls, output.readSyscalls)
        assertEquals(expectedWriteSyscalls, output.writeSyscalls)
    }
}

internal class ProcIoReaderFailureTest {
    private lateinit var file: File

    @Before
    fun setup() {
        file = File.createTempFile("iotest", null)
    }

    @Test
    fun parseEmptyFileReturnsFalse() {
        file.writeText("")
        val parser = ProcIoReader(file.absolutePath)
        val output = ProcIoReader.IoCounters()
        assertFalse(parser.parse(output))
        assertEquals(0L, output.readSyscalls)
        assertEquals(0L, output.writeSyscalls)
    }

    @Test
    fun parseMissingSyscrReturnsFalse() {
        copyResourceToFile("io_missing_syscr")
        val parser = ProcIoReader(file.absolutePath)
        val output = ProcIoReader.IoCounters()
        assertFalse(parser.parse(output))
    }

    @Test
    fun parseMissingSyscwReturnsFalse() {
        copyResourceToFile("io_missing_syscw")
        val parser = ProcIoReader(file.absolutePath)
        val output = ProcIoReader.IoCounters()
        assertFalse(parser.parse(output))
    }

    @Test
    fun parseNonExistentFileReturnsFalse() {
        val parser = ProcIoReader("/proc/does-not-exist-${System.nanoTime()}")
        val output = ProcIoReader.IoCounters()
        assertFalse(parser.parse(output))
    }

    @Test
    fun ioCountersDefaultConstructorIsZeroed() {
        val counters = ProcIoReader.IoCounters()
        assertEquals(0L, counters.readSyscalls)
        assertEquals(0L, counters.writeSyscalls)
        assertEquals(0L, counters.totalSyscalls)
    }

    private fun copyResourceToFile(resourceName: String) {
        file.outputStream().buffered().use { out ->
            requireNotNull(this::class.java.getResourceAsStream("/io/$resourceName")) {
                "cannot open $resourceName"
            }.use { it.copyTo(out) }
        }
    }
}
