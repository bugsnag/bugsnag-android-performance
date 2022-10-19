package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Logger

object NoopLogger : Logger {
    override fun e(msg: String) = Unit
    override fun e(msg: String, throwable: Throwable) = Unit
    override fun w(msg: String) = Unit
    override fun w(msg: String, throwable: Throwable) = Unit
    override fun i(msg: String) = Unit
    override fun i(msg: String, throwable: Throwable) = Unit
    override fun d(msg: String) = Unit
    override fun d(msg: String, throwable: Throwable) = Unit
}
