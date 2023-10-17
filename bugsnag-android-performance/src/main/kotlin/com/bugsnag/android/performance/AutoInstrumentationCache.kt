package com.bugsnag.android.performance

import android.app.Activity
import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class AutoInstrumentationCache {
    private val appStartActivitiesCache = HashMap<Class<out Activity>, Boolean>()
    private val autoInstrumentCache = HashMap<Class<*>, Boolean>()

    /**
     * Class does not need instrumentation.
     */
    fun isInstrumentationEnabled(jclass: Class<*>): Boolean {
        return autoInstrumentCache.getOrPut(jclass) {
            !jclass.isAnnotationPresent(DoNotInstrument::class.java)
        }
    }

    /**
     * Activities do not need to be ended.
     */
    fun isAppStartActivity(jclass: Class<out Activity>): Boolean {
        return appStartActivitiesCache.getOrPut(jclass) {
            jclass.isAnnotationPresent(DoNotEndAppStart::class.java)
        }
    }

    fun configuration(
        appStartActivities: Collection<Class<out Activity>>,
        autoInstrument: Collection<Class<*>>
    ) {
        appStartActivities.forEach { appStartActivitiesCache[it] = true }
        autoInstrument.forEach { autoInstrumentCache[it] = true }
    }
}