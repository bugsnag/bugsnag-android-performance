package com.bugsnag.android.performance.test;

import com.bugsnag.android.performance.internal.processing.Timeout;
import com.bugsnag.android.performance.internal.processing.TimeoutExecutor;

import org.jetbrains.annotations.NotNull;

import java.util.PriorityQueue;

public class TestTimeoutExecutor implements TimeoutExecutor {
    private final PriorityQueue<Timeout> timeouts = new PriorityQueue<>();

    public void runAllTimeouts() {
        while (!timeouts.isEmpty()) {
            Timeout timeout = timeouts.poll();
            if (timeout != null) {
                timeout.run();
            }
        }
    }

    @Override
    public void scheduleTimeout(@NotNull Timeout timeout) {
        timeouts.add(timeout);
    }

    @Override
    public void cancelTimeout(@NotNull Timeout timeout) {
        timeouts.remove(timeout);
    }
}
