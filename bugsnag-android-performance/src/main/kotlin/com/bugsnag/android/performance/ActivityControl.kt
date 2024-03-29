package com.bugsnag.android.performance

/**
 * Annotated Activities do not end the AppStart span when their ViewLoad is
 * ended.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class DoNotEndAppStart

/**
 * Annotation Activities and Fragments are ignored by automatic instrumentation.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class DoNotAutoInstrument
