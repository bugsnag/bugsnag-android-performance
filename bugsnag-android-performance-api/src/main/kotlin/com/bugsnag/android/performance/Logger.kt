package com.bugsnag.android.performance

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
    public fun e(msg: String, throwable: Throwable)

    /**
     * Logs a message at the warning level.
     */
    public fun w(msg: String)

    /**
     * Logs a message at the warning level.
     */
    public fun w(msg: String, throwable: Throwable)

    /**
     * Logs a message at the info level.
     */
    public fun i(msg: String)

    /**
     * Logs a message at the info level.
     */
    public fun i(msg: String, throwable: Throwable)

    /**
     * Logs a message at the debug level.
     */
    public fun d(msg: String)

    /**
     * Logs a message at the debug level.
     */
    public fun d(msg: String, throwable: Throwable)

    public companion object Global : Logger {
        public var delegate: Logger? = null

        override fun e(msg: String) {
            delegate?.e(msg)
        }

        override fun e(msg: String, throwable: Throwable) {
            delegate?.e(msg, throwable)
        }

        override fun w(msg: String) {
            delegate?.w(msg)
        }

        override fun w(msg: String, throwable: Throwable) {
            delegate?.w(msg, throwable)
        }

        override fun i(msg: String) {
            delegate?.i(msg)
        }

        override fun i(msg: String, throwable: Throwable) {
            delegate?.i(msg, throwable)
        }

        override fun d(msg: String) {
            delegate?.d(msg)
        }

        override fun d(msg: String, throwable: Throwable) {
            delegate?.d(msg, throwable)
        }
    }
}
