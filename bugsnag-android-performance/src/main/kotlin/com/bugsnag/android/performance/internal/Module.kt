package com.bugsnag.android.performance.internal

interface Module {
    fun load(tracer: Tracer)
    fun unload()
}