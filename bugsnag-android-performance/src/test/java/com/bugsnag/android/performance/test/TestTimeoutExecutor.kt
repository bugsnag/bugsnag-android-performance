package com.bugsnag.android.performance.test

import com.bugsnag.android.performance.internal.processing.Timeout
import com.bugsnag.android.performance.internal.processing.TimeoutExecutor
import java.util.PriorityQueue

internal class TestTimeoutExecutor : TimeoutExecutor {
    internal val timeouts = PriorityQueue<Timeout>()

    fun runAllTimeouts() {
        var timeout = timeouts.poll()
        while (timeout != null) {
            timeout.run()
            timeout = timeouts.poll()
        }
    }

    override fun scheduleTimeout(timeout: Timeout) {
        timeouts.add(timeout)
    }

    override fun cancelTimeout(timeout: Timeout) {
        timeouts.remove(timeout)
    }
}
