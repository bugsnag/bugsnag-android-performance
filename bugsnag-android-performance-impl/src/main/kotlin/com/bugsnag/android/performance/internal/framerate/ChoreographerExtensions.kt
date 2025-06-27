package com.bugsnag.android.performance.internal.framerate

import android.annotation.SuppressLint
import android.view.Choreographer
import java.lang.reflect.Field

@SuppressLint("PrivateApi")
private val choreographerLastFrameTimeField: Field? =
    try {
        Choreographer::class.java.getDeclaredField("mLastFrameTimeNanos").apply {
            isAccessible = true
        }
    } catch (_: Exception) {
        null
    }

/**
 * Access to the `mLastFrameTimeNanos` timestamp in the Choreographer class via reflection, which
 * is based on `System.nanoTime()`.
 */
internal val Choreographer.lastFrameTimeNanos: Long?
    get() =
        try {
            choreographerLastFrameTimeField?.get(this) as Long?
        } catch (_: Exception) {
            null
        }
