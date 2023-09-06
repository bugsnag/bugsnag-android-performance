package com.bugsnag.android.performance

class NetworkRequestInfo(
    /**
     * The URL that will be reported in this network request's span.
     * If null, no span will be created.
     */
    var url: String? = null,
)
