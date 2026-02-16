package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.BugsnagName
import java.util.concurrent.ConcurrentHashMap

public class ObjectNames {
    private val cache = ConcurrentHashMap<Class<*>, String>()

    public operator fun get(obj: Any): String {
        val objClass = obj::class.java

        var objName = cache[objClass]
        if (objName == null) {
            val newName = calculateName(objClass)
            val racedName = cache.putIfAbsent(objClass, newName)
            objName = racedName ?: newName
        }
        return objName
    }

    private fun calculateName(objClass: Class<out Any>): String {
        val annotation = objClass.getAnnotation(BugsnagName::class.java)
        return annotation?.value ?: objClass.simpleName
    }
}
