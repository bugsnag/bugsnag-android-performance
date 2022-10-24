package com.bugsnag.android.performance

enum class ViewType(internal val typeName: String, internal val spanName: String) {
    ACTIVITY("activity", "Activity"),
    FRAGMENT("fragment", "Fragment"),
    COMPOSE("compose", "Compose");
}
