package com.bugsnag.android.performance

/**
 * The type of view / ui element being measured.
 *
 * @see BugsnagPerformance.startViewLoadSpan
 */
public enum class ViewType(internal val typeName: String, internal val spanName: String) {
    ACTIVITY("activity", "Activity"),
    FRAGMENT("fragment", "Fragment"),
    COMPOSE("compose", "Compose");
}
