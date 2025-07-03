package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class SamplerTask(
    private val delivery: Delivery,
    private val sampler: ProbabilitySampler,
    private val persistentState: PersistentState,
) : Task, NewProbabilityCallback {
    override fun onAttach(worker: Worker) {
        if (!isProbabilityValid()) {
            delivery.fetchCurrentProbability()
        } else {
            sampler.sampleProbability = persistentState.pValue
        }
    }

    override fun execute(): Boolean {
        if (!isProbabilityValid()) {
            delivery.fetchCurrentProbability()
        }

        return false
    }

    override fun onNewProbability(newP: Double) {
        sampler.sampleProbability = newP

        persistentState.update {
            pValue = newP
            pValueExpiryTime = newExpiryTime()
        }
    }

    private fun isProbabilityValid(): Boolean = System.currentTimeMillis() < persistentState.pValueExpiryTime

    private fun newExpiryTime() = System.currentTimeMillis() + InternalDebug.pValueExpireAfterMs
}
