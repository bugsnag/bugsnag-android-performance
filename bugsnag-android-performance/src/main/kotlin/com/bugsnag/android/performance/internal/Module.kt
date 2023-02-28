package com.bugsnag.android.performance.internal

interface Module {
    fun load(instrumentedAppState: InstrumentedAppState)
    fun unload()

    class Loader(private val instrumentedAppState: InstrumentedAppState) : (String) -> Module? {
        fun loadModule(className: String): Module? {
            return try {
                val clazz = Class.forName(className)
                val module = clazz.newInstance() as Module

                module.also { it.load(instrumentedAppState) }
            } catch (ex: Exception) {
                null
            }
        }

        override fun invoke(className: String): Module? = loadModule(className)
    }
}
