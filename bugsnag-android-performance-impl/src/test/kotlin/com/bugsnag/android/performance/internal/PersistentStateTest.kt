package com.bugsnag.android.performance.internal

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PersistentStateTest {
    lateinit var stateFile: File

    @Before
    fun createStateFile() {
        stateFile = File.createTempFile("persistent-state", ".json")
    }

    @After
    fun deleteStateFile() {
        stateFile.delete()
    }

    @Test
    fun updateAndSave() {
        val persistentState = PersistentState(stateFile)
        persistentState.update {
            pValue = 0.01234
            pValueExpiryTime = Long.MAX_VALUE
        }

        val newPersistentState = PersistentState(stateFile)
        assertEquals(persistentState.pValue, newPersistentState.pValue, 0.000001)
        assertEquals(persistentState.pValueExpiryTime, newPersistentState.pValueExpiryTime)
    }
}
