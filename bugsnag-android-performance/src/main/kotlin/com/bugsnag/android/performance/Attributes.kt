package com.bugsnag.android.performance

class Attributes : Collection<Pair<String, Any>> {
    private val content = mutableMapOf<String, Any>()

    override val size: Int
        get() = content.size

    val keys get() = content.keys.toSet()

    operator fun set(name: String, value: String?) {
        if(value != null) {
            content[name] = value
        } else {
            content.remove(name)
        }
    }

    operator fun set(name: String, value: Long) {
        content[name] = value
    }

    operator fun set(name: String, value: Int) {
        content[name] = value.toLong()
    }

    operator fun set(name: String, value: Double) {
        content[name] = value
    }

    operator fun set(name: String, value: Boolean) {
        content[name] = value
    }

    fun remove(name: String) {
        content.remove(name)
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
