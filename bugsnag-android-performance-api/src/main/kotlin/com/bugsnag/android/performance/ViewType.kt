package com.bugsnag.android.performance

/**
 * The type of view / ui element being measured.
 *
 * @see BugsnagPerformance.startViewLoadSpan
 */
public enum class ViewType {
    ACTIVITY,
    FRAGMENT,
    COMPOSE,
}
