package com.bugsnag.android.performance

/**
 * Sets which metrics (if any) are gathered and reported.
 */
public class EnabledMetrics(
    /**
     * Determines whether rendering metrics (slow and frozen frames) are gathered and reported
     */
    public var rendering: Boolean = false,
    /**
     * Determines whether CPU use is gathered and reported. When enabled, the CPU use is
     * sampled periodically for the app process and main thread.
     */
    public var cpu: Boolean = false,
    /**
     * Determines whether memory consumption is gathered and reported.
     */
    public var memory: Boolean = false,
    /**
     * Determines whether disk I/O metrics is gathered and reported. When enabled. read/write
     * syscall rates (IOPS) are captured for spans that requests disk metrics
     */
    public val disk: Boolean = false,
) {
    public constructor(enable: Boolean) : this(enable, enable, enable, enable)

    override fun equals(other: Any?): Boolean {
        return other is EnabledMetrics &&
                rendering == other.rendering &&
                cpu == other.cpu &&
                memory == other.memory &&
                disk == other.disk
    }

    override fun hashCode(): Int {
        var result = rendering.hashCode()
        result = 31 * result + cpu.hashCode()
        result = 31 * result + memory.hashCode()
        result = 31 * result + disk.hashCode()
        return result
    }

    override fun toString(): String {
        return "EnabledMetrics(rendering=$rendering, cpu=$cpu, memory=$memory, disk=$disk)"
    }
}
