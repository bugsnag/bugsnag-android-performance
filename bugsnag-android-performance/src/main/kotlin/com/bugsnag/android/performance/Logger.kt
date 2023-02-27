package com.bugsnag.android.performance

import com.bugsnag.android.performance.internal.DebugLogger

/**
 * Logs internal messages from within the bugsnag notifier.
 */
interface Logger {

    /**
     * Logs a message at the error level.
     */
    fun e(msg: String)

    /**
     * Logs a message at the error level.
     */
    fun e(msg: String, throwable: Throwable)

    /**
     * Logs a message at the warning level.
     */
    fun w(msg: String)

    /**
     * Logs a message at the warning level.
     */
    fun w(msg: String, throwable: Throwable)

    /**
     * Logs a message at the info level.
     */
    fun i(msg: String)

    /**
     * Logs a message at the info level.
     */
    fun i(msg: String, throwable: Throwable)

    /**
     * Logs a message at the debug level.
     */
    fun d(msg: String)

    /**
     * Logs a message at the debug level.
     */
    fun d(msg: String, throwable: Throwable)

    companion object Global : Logger {
        var delegate: Logger = DebugLogger
            internal set

        override fun e(msg: String) = delegate.e(msg)
        override fun e(msg: String, throwable: Throwable) = delegate.e(msg, throwable)
        override fun w(msg: String) = delegate.w(msg)
        override fun w(msg: String, throwable: Throwable) = delegate.w(msg, throwable)
        override fun i(msg: String) = delegate.i(msg)
        override fun i(msg: String, throwable: Throwable) = delegate.i(msg, throwable)
        override fun d(msg: String) = delegate.d(msg)
        override fun d(msg: String, throwable: Throwable) = delegate.d(msg, throwable)
    }
}
