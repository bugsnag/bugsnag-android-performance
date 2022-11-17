package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.HasAttributes

internal class DefaultAttributeSource(
    private val connectivity: Connectivity,
) : AttributeSource {
    override fun invoke(target: HasAttributes) {
        target.setAttribute("net.host.connection.type", connectivity.networkType.otelName)

        connectivity.networkSubType?.let { networkSubtype ->
            target.setAttribute("net.host.connection.subtype", networkSubtype)
        }
    }
}
