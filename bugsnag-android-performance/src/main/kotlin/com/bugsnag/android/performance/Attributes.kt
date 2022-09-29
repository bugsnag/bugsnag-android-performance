package com.bugsnag.android.performance

class Attributes : Collection<Pair<String, Any>> {
    private val content = mutableMapOf<String, Any>()

    override val size: Int
        get() = content.size

    val keys get() = content.keys.toSet()

    operator fun set(key: String, value: String) {
        content[key] = value
    }

    operator fun set(key: String, value: Long) {
        content[key] = value
    }

    operator fun set(key: String, value: Double) {
        content[key] = value
    }

    operator fun set(key: String, value: Boolean) {
        content[key] = value
    }

    override fun iterator(): Iterator<Pair<String, Any>> =
        content.asSequence().map { it.toPair() }.iterator()

    override fun contains(element: Pair<String, Any>): Boolean {
        val (key, value) = element
        return content[key] == value
    }

    override fun containsAll(elements: Collection<Pair<String, Any>>): Boolean {
        return elements.all { (key, value) -> content[key] == value }
    }

    override fun isEmpty(): Boolean = content.isEmpty()
}
