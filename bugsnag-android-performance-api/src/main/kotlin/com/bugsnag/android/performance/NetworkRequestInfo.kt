package com.bugsnag.android.performance

public class NetworkRequestInfo(
    /**
     * The URL that will be reported in this network request's span.
     * If null, no span will be created.
     */
    public var url: String? = null,
)
