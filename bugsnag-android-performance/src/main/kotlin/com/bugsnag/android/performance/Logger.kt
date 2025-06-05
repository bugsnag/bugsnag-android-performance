package com.bugsnag.android.performance

import com.bugsnag.android.performance.internal.DebugLogger

/**
 * Logs internal messages from within the bugsnag notifier.
 */
public interface Logger {
    /**
     * Logs a message at the error level.
     */
    public fun e(msg: String)

    /**
     * Logs a message at the error level.
     */
    public fun e(
        msg: String,
        throwable: Throwable,
    )

    /**
     * Logs a message at the warning level.
     */
    public fun w(msg: String)

    /**
     * Logs a message at the warning level.
     */
    public fun w(
        msg: String,
        throwable: Throwable,
    )

    /**
     * Logs a message at the info level.
     */
    public fun i(msg: String)

    /**
     * Logs a message at the info level.
     */
    public fun i(
        msg: String,
        throwable: Throwable,
    )

    /**
     * Logs a message at the debug level.
     */
    public fun d(msg: String)

    /**
     * Logs a message at the debug level.
     */
    public fun d(
        msg: String,
        throwable: Throwable,
    )

    public companion object Global : Logger {
        public var delegate: Logger = DebugLogger
            internal set

        override fun e(msg: String): Unit = delegate.e(msg)

        override fun e(
            msg: String,
            throwable: Throwable,
        ): Unit = delegate.e(msg, throwable)

        override fun w(msg: String): Unit = delegate.w(msg)

        override fun w(
            msg: String,
            throwable: Throwable,
        ): Unit = delegate.w(msg, throwable)

        override fun i(msg: String): Unit = delegate.i(msg)

        override fun i(
            msg: String,
            throwable: Throwable,
        ): Unit = delegate.i(msg, throwable)

        override fun d(msg: String): Unit = delegate.d(msg)

        override fun d(
            msg: String,
            throwable: Throwable,
        ): Unit = delegate.d(msg, throwable)
    }
}
