package com.bugsnag.android.performance

/**
 * Declares that a class contains [Attributes] and provides convenience functions to modify the
 * attributes directly without needing to access the [attributes] property.
 */
interface HasAttributes {
    val attributes: Attributes

    /**
     * Set or clear a string attribute. Passing `null` is the same as calling [clearAttribute] for
     * the given `name`.
     *
     * @param name the attribute name
     * @param value the value to set the attribute to
     */
    fun setAttribute(name: String, value: String?) {
        attributes[name] = value
    }

    /**
     * Set a long integer attribute.
     *
     * @param name the attribute name
     * @param value the value to set the attribute to
     */
    fun setAttribute(name: String, value: Long) {
        attributes[name] = value
    }

    /**
     * Set an integer attribute.
     *
     * @param name the attribute name
     * @param value the value to set the attribute to
     */
    fun setAttribute(name: String, value: Int) {
        attributes[name] = value
    }

    /**
     * Set a floating point / double attribute.
     *
     * @param name the attribute name
     * @param value the value to set the attribute to
     */
    fun setAttribute(name: String, value: Double) {
        attributes[name] = value
    }

    /**
     * Set a boolean attribute.
     *
     * @param name the attribute name
     * @param value the value to set the attribute to
     */
    fun setAttribute(name: String, value: Boolean) {
        attributes[name] = value
    }

    /**
     * Clear / remove the specified attribute if it exists. This is the same
     * as `attributes.remove(name)`.
     */
    fun clearAttribute(name: String) {
        attributes.remove(name)
    }
}
