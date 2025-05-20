package com.bugsnag.android.performance.controls

/**
 * Marker interface for classes that can be used with [SpanControlProvider]. This interface does
 * not define any specific methods or properties.
 *
 * @param C The type of span control returned for this query
 */
public interface SpanQuery<out C>

/**
 * Provides a way to retrieve a span control based on a [SpanQuery]. Span controls are specialised
 * objects that allow for the manipulation of spans without directly referencing the [Span] object,
 * allowing for specialized operations to be performed on spans.
 *
 * @param C The type of span control returned by the provider
 */
public interface SpanControlProvider<C> {
    /**
     * If possible retrieves the span controls the given [query]. If the query cannot be fulfilled
     * by this provider, null is returned.
     *
     * @param query The query to be used to retrieve the span control
     * @return the span control for the query, or null if no match is found or the query cannot be
     *      fulfilled by this provider
     */
    public operator fun <Q : SpanQuery<C>> get(query: Q): C?
}
