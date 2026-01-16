package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.BugsnagName

public class ObjectNames {
    public operator fun get(obj: Any): String {
        val objClass = obj::class.java
        val annotation = objClass.getAnnotation(BugsnagName::class.java)
        return annotation?.value ?: obj::class.java.simpleName
    }
}
