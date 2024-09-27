package com.bugsnag.android.performance.internal.framerate

internal class RenderMetricsSnapshot(
    val slowFrameCount: Long,
    val frozenFrameCount: Long,
    val totalFrameCount: Long,
    val frozenFrame: FrozenFrame?,
) {
    inline fun forEachFrozenFrameUntil(
        end: RenderMetricsSnapshot,
        consumer: (Long, Long) -> Unit,
    ) {
        if (end.totalFrameCount <= totalFrameCount) {
            // early exit, the "end" snapshot is before or the same as the "start" snapshot (this)
            return
        }

        val endFrame = end.frozenFrame
        // we skip the "first" frozen frame, as it would have ended before the snapshot was taken
        var currentFrame: FrozenFrame? = frozenFrame?.next
        while (currentFrame != null) {
            consumer(currentFrame.startTimestamp, currentFrame.endTimestamp)

            if (currentFrame === endFrame) {
                break
            }

            currentFrame = currentFrame.next
        }
    }
}

internal class FrozenFrame(
    val startTimestamp: Long,
    val endTimestamp: Long,
) {
    /**
     * FrozenFrames form a singly linked list that allow snapshots to keep track of only the
     * "latest" frozen frame, and then follow the links forward to an "ending" snapshot
     * to find all of the frozen frames that happened within a given time window.
     *
     * The forward-only, single-link nature of this list makes any "old" frozen frames GC eligible.
     */
    var next: FrozenFrame? = null
}

internal class RenderMetricsContainer {
    @Volatile
    private var totalMetricsCount: Long = 0L
        private set

    @Volatile
    var slowFrameCount: Long = 0L
        private set

    @Volatile
    var frozenFrameCount: Long = 0L
        private set

    @Volatile
    var totalFrameCount: Long = 0L
        private set

    private var latestFrozenFrame: FrozenFrame? = null

    /**
     * Update this [RenderMetricsCollector] with data from a new frame. [newMetrics] is expected to
     * call [countSlowFrame] and [countFrozenFrame] for the new frame data, and then return the
     * actual number of frames that were rendered since the last call to [update].
     */
    inline fun update(newMetrics: RenderMetricsContainer.() -> Int) {
        totalMetricsCount++
        totalFrameCount += newMetrics()
    }

    /**
     * Count a single slow frame, typically called from [update]
     */
    fun countSlowFrame() {
        slowFrameCount++
    }

    /**
     * Count a single frozen frame and its expected start/end time, typically called from [update]
     */
    fun countFrozenFrame(start: Long, end: Long) {
        val newFrozenFrame = FrozenFrame(start, end)
        latestFrozenFrame?.next = newFrozenFrame
        latestFrozenFrame = newFrozenFrame

        frozenFrameCount++
    }

    fun snapshot(): RenderMetricsSnapshot {
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
                return RenderMetricsSnapshot(
                    slowFrameSnapshot,
                    frozenFrameSnapshot,
                    totalFrameSnapshot,
                    latestFrozenFrame,
                )
            }
        }
    }
}
