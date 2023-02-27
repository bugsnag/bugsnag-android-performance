package com.bugsnag.android.performance.test

import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaField

/*
 * Similar to ReflectionHelpers.setStaticField but made safer by Kotlin KProperty use
 */
inline fun <reified T> setStatic(property: KProperty<T>, value: T) {
    val field = property.javaField!!
    field.isAccessible = true
    field.set(null, value)
}
