package com.bugsnag.android.performance.internal

interface Module {
    fun load()
    fun unload()
}