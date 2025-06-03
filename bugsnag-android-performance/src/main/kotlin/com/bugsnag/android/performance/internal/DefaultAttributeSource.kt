package com.bugsnag.android.performance.internal

import java.util.concurrent.atomic.AtomicReference

internal class DefaultAttributeSource : AttributeSource {
    internal var currentDefaultAttributes =
        AtomicReference(
            DefaultAttributes(NetworkType.UNKNOWN, null, isInForeground()),
        )

    /**
     * Safely update the default set of attributes in this `DefaultAttributeSource`. This method
     * is thread safe and lock-free, so [updateAttributes] is expected to be side-effect free as it
     * may be invoked any number of times before returning.
     */
    internal inline fun update(updateAttributes: (DefaultAttributes) -> DefaultAttributes) {
        while (true) {
            val attributes = currentDefaultAttributes.get()
            val newAttributes = updateAttributes(attributes)

            // exit if no changes were actually made
            if (attributes == newAttributes) {
                return
            }

            if (currentDefaultAttributes.compareAndSet(attributes, newAttributes)) {
                return
            }
        }
    }

    override fun invoke(target: SpanImpl) {
        val defaultAttributes = currentDefaultAttributes.get()
        target.attributes["net.host.connection.type"] = defaultAttributes.networkType.otelName

        defaultAttributes.networkSubType?.let { networkSubtype ->
            target.attributes["net.host.connection.subtype"] = networkSubtype
        }

        defaultAttributes.isInForeground?.let { inForeground ->
            target.attributes["bugsnag.app.in_foreground"] = inForeground
        }
    }
}

internal data class DefaultAttributes(
    val networkType: NetworkType,
    val networkSubType: String?,
    val isInForeground: Boolean?,
)
