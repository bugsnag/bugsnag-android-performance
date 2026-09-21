package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.Logger

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class SamplerTask(
    private val delivery: Delivery,
    private val sampler: ProbabilitySampler,
    private val persistentState: PersistentState,
) : Task, NewProbabilityCallback {
    override fun onAttach(worker: Worker) {
        if (!isProbabilityValid()) {
            Logger.d("App session config cache expired - fetching sampling probability from server")
            delivery.fetchCurrentProbability()
        } else {
            sampler.sampleProbability = persistentState.pValue
            Logger.d(
                "App session config cache hit - using sampling probability ${persistentState.pValue} " +
                    "until ${persistentState.pValueExpiryTime}",
            )
        }
    }

    override fun execute(): Boolean {
        if (!isProbabilityValid()) {
            Logger.d("App session config cache expired - refreshing sampling probability from server")
            delivery.fetchCurrentProbability()
        }

        return false
    }

    override fun onNewProbability(newP: Double) {
        Logger.d("App session config response received - new sampling probability=$newP")
        sampler.sampleProbability = newP

        persistentState.update {
            pValue = newP
            pValueExpiryTime = newExpiryTime()
        }

        Logger.d("App session config persisted - expires at ${persistentState.pValueExpiryTime}")
    }

    private fun isProbabilityValid(): Boolean = System.currentTimeMillis() < persistentState.pValueExpiryTime

    private fun newExpiryTime() = System.currentTimeMillis() + InternalDebug.pValueExpireAfterMs
}
