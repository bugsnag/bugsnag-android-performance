package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public interface Module {
    public fun load(instrumentedAppState: InstrumentedAppState)

    public fun unload()

    public class Loader(private val instrumentedAppState: InstrumentedAppState) :
        (String) -> Module? {
        public fun loadModule(className: String): Module? {
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
