package com.bugsnag.android.performance.internal.framerate

internal class FramerateMetricsSnapshot(
    val slowFrameCount: Long,
    val frozenFrameCount: Long,
    val totalFrameCount: Long,
    val frozenFrames: TimestampPairBuffer,
    val firstFrozenFrameIndex: Int,
) {
    inline fun forEachFrozenFrameUntil(
        end: FramerateMetricsSnapshot,
        consumer: (Long, Long) -> Unit,
    ) {
        var buffer: TimestampPairBuffer? = frozenFrames
        var bufferIndex = firstFrozenFrameIndex
        while (buffer != null) {
            val endIndex =
                if (buffer === end.frozenFrames) {
                    end.firstFrozenFrameIndex
                } else {
                    buffer.timestamps.size
                }

            var index = bufferIndex
            while (index < endIndex) {
                consumer(buffer.timestamps[index], buffer.timestamps[index + 1])
                index += 2
            }

            if (buffer === end.frozenFrames) {
                break
            }

            buffer = buffer.next
            bufferIndex = 0
        }
    }
}

/**
 * A LinkedList of LongArrays that we can quickly and easily store timestamp pairs (start/end) in.
 * These can then be used to construct Spans (such as those for frozen frames) once off the
 * hot-path. The forward-only nature of the chain makes them GC eligible when appropriate without
 * having to track "open" snapshot groups.
 */
internal class TimestampPairBuffer(size: Int = DEFAULT_BUFFER_SIZE) {
    var index = 0
        private set

    val timestamps: LongArray = LongArray(size)
    var next: TimestampPairBuffer? = null

    fun add(
        start: Long,
        end: Long,
    ): Boolean {
        if (index >= timestamps.size) {
            return false
        }

        timestamps[index++] = start
        timestamps[index++] = end
        return true
    }

    companion object {
        const val DEFAULT_BUFFER_SIZE = 64
    }
}

internal class FramerateMetricsContainer {
    @Volatile
    var totalMetricsCount: Long = 0L

    @Volatile
    var slowFrameCount: Long = 0L

    @Volatile
    var frozenFrameCount: Long = 0L

    @Volatile
    var totalFrameCount: Long = 0L

    var frozenFrames: TimestampPairBuffer = TimestampPairBuffer()

    fun addFrozenFrame(
        start: Long,
        end: Long,
    ) {
        while (!frozenFrames.add(start, end)) {
            val newFrozenFrameBuffer = TimestampPairBuffer()
            frozenFrames.next = newFrozenFrameBuffer
            frozenFrames = newFrozenFrameBuffer
        }

        frozenFrameCount++
    }

    fun snapshot(): FramerateMetricsSnapshot {
        while (true) {
            // always capture the totalMetricsCount *first*
            val totalMetricsSnapshot = this.totalMetricsCount
            val totalFrameSnapshot = this.totalFrameCount
            val slowFrameSnapshot = this.slowFrameCount
            val frozenFrameSnapshot = this.frozenFrameCount

            // now check that the totalFrameCount & totalMetricsCount hasn't changed
            // this allows a sort of "very optimistic lock" without actually locking
            if (totalFrameSnapshot == this.totalFrameCount &&
                totalMetricsSnapshot == this.totalMetricsCount
            ) {
                // exit the loop if everything appear stable
                return FramerateMetricsSnapshot(
                    slowFrameSnapshot,
                    frozenFrameSnapshot,
                    totalFrameSnapshot,
                    frozenFrames,
                    frozenFrames.index,
                )
            }
        }
    }
}
