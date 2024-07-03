package com.bugsnag.android.performance

import com.bugsnag.android.performance.internal.SpanImpl

/**
 * Test utility function that attempts to extract the currently enqueued batch of spans without
 * mocking anything. This works because `BugsnagPerformance` allows spans to be opened & ended
 * before `start` is called.
 *
 * Once this method is called, the "current" batch is cleared (hence the `take` naming).
 */
fun BugsnagPerformance.takeCurrentBatch(): Collection<SpanImpl> {
    return BugsnagPerformanceHooks.takeCurrentBatch()
}
