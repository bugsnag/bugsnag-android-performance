package com.bugsnag.android.performance

/**
 * Declares that a class contains [Attributes] and provides convenience functions to modify the
 * attributes directly without needing to access the [attributes] property.
 */
public interface HasAttributes {

    /**
     * Set or clear a string attribute. Passing `null` will remove the attribute.
     *
     * @param name the attribute name
     * @param value the value to set the attribute to
     */
    public fun setAttribute(name: String, value: String?)

    /**
     * Set a long integer attribute.
     *
     * @param name the attribute name
     * @param value the value to set the attribute to
     */
    public fun setAttribute(name: String, value: Long)

    /**
     * Set an integer attribute.
     *
     * @param name the attribute name
     * @param value the value to set the attribute to
     */
    public fun setAttribute(name: String, value: Int)

    /**
     * Set a floating point / double attribute.
     *
     * @param name the attribute name
     * @param value the value to set the attribute to
     */
    public fun setAttribute(name: String, value: Double)

    /**
     * Set a boolean attribute.
     *
     * @param name the attribute name
     * @param value the value to set the attribute to
     */
    public fun setAttribute(name: String, value: Boolean)

    /**
     * Set a collection of values.
     *
     * @param name the attribute name
     * @param value the value to set the attribute to
     */
    public fun setAttribute(name: String, value: Collection<Any>)

    /**
     * Set an array of integer values.
     *
     * @param name the attribute name
     * @param value the value to set the attribute to
     */
    public fun setAttribute(name: String, value: IntArray?)

    /**
     * Set an array of long values.
     *
     * @param name the attribute name
     * @param value the value to set the attribute to
     */
    public fun setAttribute(name: String, value: LongArray?)

    /**
     * Set an array of double values.
     *
     * @param name the attribute name
     * @param value the value to set the attribute to
     */
    public fun setAttribute(name: String, value: DoubleArray?)
}
