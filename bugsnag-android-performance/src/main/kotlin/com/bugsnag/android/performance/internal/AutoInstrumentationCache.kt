package com.bugsnag.android.performance.internal

import android.app.Activity
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.DoNotAutoInstrument
import com.bugsnag.android.performance.DoNotEndAppStart

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AutoInstrumentationCache {
    private val appStartActivitiesCache = HashMap<Class<out Activity>, Boolean>()
    private val autoInstrumentCache = HashMap<Class<*>, Boolean>()

    /**
     * Class does not need instrumentation.
     */
    public fun isInstrumentationEnabled(jclass: Class<*>): Boolean {
        return autoInstrumentCache.getOrPut(jclass) {
            !jclass.isAnnotationPresent(DoNotAutoInstrument::class.java)
        }
    }

    /**
     * Activities do not need to be ended.
     */
    public fun isAppStartActivity(jclass: Class<out Activity>): Boolean {
        return appStartActivitiesCache.getOrPut(jclass) {
            jclass.isAnnotationPresent(DoNotEndAppStart::class.java)
        }
    }

    public fun configure(
        appStartActivities: Collection<Class<out Activity>>,
        autoInstrument: Collection<Class<*>>,
    ) {
        appStartActivities.forEach { appStartActivitiesCache[it] = true }
        autoInstrument.forEach { autoInstrumentCache[it] = false }
    }
}
