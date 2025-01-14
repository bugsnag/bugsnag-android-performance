package com.bugsnag.android.performance.internal

import android.app.Activity
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.DoNotAutoInstrument
import com.bugsnag.android.performance.DoNotEndAppStart
import com.bugsnag.android.performance.internal.processing.ImmutableConfig

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AutoInstrumentationCache {
    private val appStartActivitiesCache = HashMap<Class<out Activity>, Boolean>()
    private val autoInstrumentCache = HashMap<Class<*>, Boolean>()

    private var instrumentActivityStart: Boolean = true
    private var instrumentActivityEnd: Boolean = true

    public fun shouldAutoStartSpan(target: Any): Boolean {
        return when {
            !isInstrumentationEnabled(target::class.java) -> false
            target is Activity -> instrumentActivityStart
            else -> true
        }
    }

    public fun shouldAutoEndSpan(target: Any): Boolean {
        return when {
            !isInstrumentationEnabled(target::class.java) -> false
            target is Activity -> instrumentActivityEnd
            else -> true
        }
    }

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

    internal fun configure(configuration: ImmutableConfig) {
        instrumentActivityStart = configuration.autoInstrumentActivities != AutoInstrument.OFF
        instrumentActivityEnd = configuration.autoInstrumentActivities == AutoInstrument.FULL
        configuration.doNotEndAppStart.forEach { appStartActivitiesCache[it] = true }
        configuration.doNotAutoInstrument.forEach { autoInstrumentCache[it] = false }
    }
}
