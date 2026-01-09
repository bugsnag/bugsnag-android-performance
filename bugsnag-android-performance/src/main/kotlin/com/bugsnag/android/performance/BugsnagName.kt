package com.bugsnag.android.performance

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.TYPE

/**
 * Used to specify a name for a class when automatically naming spans after instances of this type. This overrides
 * any defaults derived (such as the simple class name). These names are used verbatim in the dashboard and
 * do not need to be unique.
 */
@Retention(RUNTIME)
@Target(TYPE)
public annotation class BugsnagName(
    val value: String,
)
