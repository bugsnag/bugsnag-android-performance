package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.test.withDebugValues
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.AdditionalMatchers.gt
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class SamplerTaskTest {
    /**
     * Test that the Sampler value does not change when no result is delivered when
     * calling `fetchCurrentProbability`
     */
    @Test
    fun fetchWithoutResponse() {
        val delivery = mock<Delivery>()
        val sampler = ProbabilitySampler(0.23)
        val samplerTask = SamplerTask(delivery, sampler, mock())

        samplerTask.onAttach(mock())
        samplerTask.execute()

        verify(delivery, times(2)).fetchCurrentProbability()

        assertEquals(0.23, sampler.sampleProbability, 0.001)
    }

    /**
     * Test that `fetchCurrentProbability` is never called if the probability has not expired.
     */
    @Test
    fun probabilityNotExpired() =
        InternalDebug.withDebugValues {
            InternalDebug.pValueExpireAfterMs = 200

            val delivery = mock<Delivery>()
            val sampler = ProbabilitySampler(1.0)
            val persistentState =
                mock<PersistentState> {
                    whenever(it.pValue) doReturn 0.5
                    whenever(it.pValueExpiryTime) doAnswer {
                        System.currentTimeMillis() + (InternalDebug.pValueExpireAfterMs * 2)
                    }
                }

            val samplerTask = SamplerTask(delivery, sampler, persistentState)

            samplerTask.onAttach(mock())
            samplerTask.execute()

            // the probability has not expired and this should never have been called
            verify(delivery, never()).fetchCurrentProbability()

            assertEquals(0.5, sampler.sampleProbability, 0.01)
        }

    @Test
    fun updateProbability() =
        InternalDebug.withDebugValues {
            InternalDebug.pValueExpireAfterMs = 200

            val delivery = mock<Delivery>()
            val sampler = ProbabilitySampler(1.0)
            val persistentState = mock<PersistentState>()

            val samplerTask = SamplerTask(delivery, sampler, persistentState)
            samplerTask.onNewProbability(0.25)

            assertEquals(0.25, sampler.sampleProbability, 0.001)

            // PersistentState.update is an inline function - we verify the setters
            verify(persistentState).pValueExpiryTime = gt(System.currentTimeMillis())
            verify(persistentState).pValue = 0.25
        }
}
