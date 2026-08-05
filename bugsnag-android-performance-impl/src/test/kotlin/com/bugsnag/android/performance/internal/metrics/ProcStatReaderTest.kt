package com.bugsnag.android.performance.internal.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import java.io.File

@RunWith(Parameterized::class)
internal class ProcStatReaderTest {
    companion object {
        @get:JvmStatic
        @get:Parameters
        val parameters =
            listOf<Pair<String, ProcStatReader.Stat>>(
                "stat1" to ProcStatReader.Stat('R', 0, 0, 0, 0, 20, 0, 1, 0, 6815, 11071946752L, 1126),
                "stat2" to ProcStatReader.Stat('S', 1, 1, 0, 0, 20, 0, 11, 0, 295206, 34206609408, 17794),
                "stat3" to ProcStatReader.Stat('S', 12, 6, 0, 0, 20, 0, 18, 0, -4717, -16747143168, -21121),
            )
    }

    @Parameter
    lateinit var testData: Pair<String, ProcStatReader.Stat>

    lateinit var file: File

    @Before
    fun setup() {
        val (statFile) = testData
        file = File.createTempFile("state", null)

        file.outputStream().buffered().use { out ->
            requireNotNull(this::class.java.getResourceAsStream("/stat/$statFile")) { "cannot open $statFile" }
                .use { it.copyTo(out) }
        }
    }

    @Test
    fun testStatParsing() {
        val (_, expected) = testData
        val parser = ProcStatReader(file.absolutePath)
        val output = ProcStatReader.Stat()
        assertTrue(parser.parse(output))
        assertEquals(expected, output)
    }
}
