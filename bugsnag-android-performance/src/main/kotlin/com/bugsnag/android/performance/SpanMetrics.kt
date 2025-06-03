package com.bugsnag.android.performance

/**
 * Determines which metrics are reported for an individual span. `null` values indicate default
 * behaviour where metrics are reported only for first class ([SpanOptions.setFirstClass])
 * spans.
 *
 * If a metric is not being gathered (due to being turned off with [EnabledMetrics]) then enabling
 * it for individual spans will have no effect.
 *
 * @see PerformanceConfiguration.enabledMetrics
 * @see SpanOptions.withMetrics
 */
public class SpanMetrics(
    /**
     * Determines whether rendering metrics (slow and frozen frames) are reported for the span.
     */
    public val rendering: Boolean? = null,
    /**
     * Determines whether CPU use is reported for the span.
     */
    public val cpu: Boolean? = null,
    /**
     * Determines whether memory consumption is reported for the span.
     */
    public val memory: Boolean? = null,
) {
    override fun equals(other: Any?): Boolean {
        return other is SpanMetrics &&
            rendering == other.rendering &&
            cpu == other.cpu &&
            memory == other.memory
    }

    override fun hashCode(): Int {
        var result = rendering.hashCode()
        result = 31 * result + cpu.hashCode()
        result = 31 * result + memory.hashCode()
        return result
    }

    override fun toString(): String {
        return "SpanMetrics(rendering=$rendering, cpu=$cpu, memory=$memory)"
    }
}
