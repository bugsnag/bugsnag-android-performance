package com.bugsnag.android.performance.test

import com.bugsnag.android.performance.internal.processing.SamplerExecutor

internal class TestSamplerExecutor : SamplerExecutor {
    private val samplers = mutableListOf<Pair<Long, Runnable>>()

    override fun addSampler(sampler: Runnable, sampleRateMs: Long) {
        samplers.add(sampleRateMs to sampler)
    }

    override fun removeSampler(sampler: Runnable) {
        samplers.removeAll { (_, s) -> s === sampler }
    }

    fun runSamplers() {
        samplers.forEach { (_, s) -> s.run() }
    }
}
