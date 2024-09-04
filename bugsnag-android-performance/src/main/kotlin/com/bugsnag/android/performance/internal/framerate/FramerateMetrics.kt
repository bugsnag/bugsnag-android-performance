package com.bugsnag.android.performance.internal.framerate

internal data class FramerateMetricsSnapshot(
    val slowFrameCount: Long,
    val frozenFrameCount: Long,
    val totalFrameCount: Long,
)

internal data class FramerateMetricsContainer(
    @Volatile
    var totalMetricsCount: Long = 0L,
    @Volatile
    var slowFrameCount: Long = 0L,
    @Volatile
    var frozenFrameCount: Long = 0L,
    @Volatile
    var totalFrameCount: Long = 0L,
) {
    fun snapshot(): FramerateMetricsSnapshot {
        while (true) {
            val totalMetricsSnapshot = this.totalMetricsCount
            // always capture the totalFrameCount *first*
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
                )
            }
        }
    }
}
