package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.Logger

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public object NoopLogger : Logger {
    override fun e(msg: String): Unit = Unit

    override fun e(
        msg: String,
        throwable: Throwable,
    ): Unit = Unit

    override fun w(msg: String): Unit = Unit

    override fun w(
        msg: String,
        throwable: Throwable,
    ): Unit = Unit

    override fun i(msg: String): Unit = Unit

    override fun i(
        msg: String,
        throwable: Throwable,
    ): Unit = Unit

    override fun d(msg: String): Unit = Unit

    override fun d(
        msg: String,
        throwable: Throwable,
    ): Unit = Unit
}
