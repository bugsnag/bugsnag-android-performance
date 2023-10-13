package com.bugsnag.android.performance

/**
 * Annotated Activities do not end the AppStart span when their ViewLoad is
 * ended.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DoesNotEndAppStart

/**
 * Annotation Activities and Fragments are ignored by automatic instrumentation.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DoNotInstrument
