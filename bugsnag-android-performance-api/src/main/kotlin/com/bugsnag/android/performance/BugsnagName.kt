package com.bugsnag.android.performance

/**
 * Used to specify a name for a class when automatically naming spans after instances of this type. This overrides
 * any defaults derived (such as the simple class name). These names are used verbatim in the dashboard and
 * do not need to be unique.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.TYPE, AnnotationTarget.CLASS)
public annotation class BugsnagName(
    val value: String,
)
