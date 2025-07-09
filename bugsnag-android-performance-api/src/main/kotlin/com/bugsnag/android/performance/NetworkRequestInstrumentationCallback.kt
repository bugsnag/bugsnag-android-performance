package com.bugsnag.android.performance

public fun interface NetworkRequestInstrumentationCallback {
    /**
     * Use this callback to filter network request URLs when generating spans.
     *
     * For example:
     *
     * ```kotlin
     * config.networkRequestCallback = NetworkRequestInstrumentationCallback { reqInfo ->
     *   val url = reqInfo.url ?: return@NetworkRequestInstrumentationCallback
     *   if (url.startsWith("http://my-api.com/uninteresting/")) {
     *     // Don't generate spans for these
     *     reqInfo.url = null
     *   } else if (url.endsWith("/changeme")) {
     *     // Generate a span, but report a different URL
     *     reqInfo.url = url.dropLast(9) + "/changed"
     *   }
     *   // Otherwise, just create a span with the given URL
     * }
     * ```
     */
    public fun onNetworkRequest(info: NetworkRequestInfo)
}
