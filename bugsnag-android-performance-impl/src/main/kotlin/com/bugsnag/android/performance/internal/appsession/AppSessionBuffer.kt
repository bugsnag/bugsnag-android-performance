package com.bugsnag.android.performance.internal.appsession

import android.content.Context
import com.bugsnag.android.performance.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe in-memory buffer for completed [AppSessionData] entries.
 *
 * ## Lifecycle
 * ```
 * app session span ends
 *      │
 *      ▼
 * buffer.add(data)          ← stored in heap LinkedBlockingDeque (immediate, blocking)
 *      │
 *      ├──────────────────────────────────────────────────────────────────────────┐
 *      │ every [persistIntervalMs]                                                │
 *      ▼                                                                          │
 * persistToDisk()           ← writes entire deque as JSON to cache file          │
 *      │                      (safe: process killed between here and delivery     │
 *      │                       → data survives in the file)                       │
 *      │                                                                          │
 *      ◄──────────────────────────────────────────────────────────────────────────┘
 *      │
 *      ▼
 * drain()                   ← called by consumer (e.g. analytics, UI) to read data
 * ```
 *
 * ## Thread safety
 * - [add] uses a [LinkedBlockingDeque] — safe from any thread.
 * - [persistToDisk] and [loadFromDisk] are guarded by [diskLock] to prevent
 *   concurrent writes from the scheduler and the [stop] flush.
 * - [drain] drains atomically under [diskLock] and re-persists the now-empty state.
 *
 * ## Disk file
 * Location: `<cacheDir>/bugsnag-performance/v1/app-session-buffer.json`
 * Format: `{ "appSessions": [ { … }, … ] }`
 */
internal class AppSessionBuffer(
    context: Context,
    /**
     * How often (milliseconds) the in-memory queue is flushed to disk.
     * Default: 30 seconds.
     */
    private val persistIntervalMs: Long = DEFAULT_PERSIST_INTERVAL_MS,
) {
    // ── In-memory store ───────────────────────────────────────────────────────
    private val heap = LinkedBlockingDeque<AppSessionData>()

    // ── Disk store ────────────────────────────────────────────────────────────
    private val bufferFile: File = File(
        File(context.cacheDir, "bugsnag-performance/v1").apply { mkdirs() },
        BUFFER_FILENAME,
    )
    private val diskLock = ReentrantLock()

    // ── Scheduler ─────────────────────────────────────────────────────────────
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "bugsnag-app-session-buffer-persist").apply { isDaemon = true }
    }
    private var persistFuture: ScheduledFuture<*>? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Startup
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Start the periodic persistence scheduler and load any previously saved app sessions from disk
     * (e.g. app sessions written before the process was killed).
     */
    fun start() {
        loadFromDisk()

        persistFuture = scheduler.scheduleWithFixedDelay(
            ::persistToDisk,
            persistIntervalMs,
            persistIntervalMs,
            TimeUnit.MILLISECONDS,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Add a completed app session to the heap buffer.
     *
     * This is lock-free and safe to call from any thread. The data will be persisted
     * to disk within [persistIntervalMs] milliseconds by the background scheduler.
     */
    fun add(data: AppSessionData) {
        heap.addLast(data)
        Logger.d(
            "AppSessionBuffer: buffered app session #${data.index} " +
                "(${data.appSessionName?.let { " \"$it\"" } ?: ""}) " +
                "reason=${data.closeReason} heap_size=${heap.size}",
        )
    }

    /**
     * Returns a snapshot of all buffered app sessions in insertion order.
     * The heap is **not** cleared — use [drain] to also remove items.
     */
    fun snapshot(): List<AppSessionData> = heap.toList()

    /**
     * Atomically drain all buffered app sessions and persist the (now empty) state to disk.
     *
     * @return all app sessions that were in the buffer at the time of the call.
     */
    fun drain(): List<AppSessionData> = diskLock.withLock {
        val items = mutableListOf<AppSessionData>()
        while (true) {
            items += heap.pollFirst() ?: break
        }
        persistUnderLock() // persist empty state — clears the file
        return items
    }

    /**
     * Immediately flush the heap buffer to disk and shut down the scheduler.
     * Call this when the SDK stops or the app is being terminated cleanly.
     */
    fun stop() {
        persistFuture?.cancel(false)
        persistToDisk() // final flush
        scheduler.shutdownNow()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Persistence
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Write the current heap snapshot to [bufferFile] as JSON.
     *
     * Called periodically by the scheduler and also on [stop].
     * Guarded by [diskLock] so concurrent calls from scheduler + [stop] are safe.
     */
    fun persistToDisk() = diskLock.withLock {
        persistUnderLock()
    }

    private fun persistUnderLock() {
        @Suppress("TooGenericExceptionCaught")
        try {
            val array = JSONArray()
            heap.forEach { data -> array.put(data.toJson()) }
            val json = JSONObject().apply { put(KEY_APP_SESSIONS, array) }
            bufferFile.writeText(json.toString())
            Logger.d("AppSessionBuffer: persisted ${heap.size} app session(s) to disk")
        } catch (ex: Exception) {
            Logger.w("AppSessionBuffer: failed to persist to disk", ex)
        }
    }

    /**
     * Load previously persisted app sessions from [bufferFile] into the heap.
     *
     * Called once on [start] to recover any app sessions that were buffered but not yet
     * drained before the process was killed.
     */
    private fun loadFromDisk() = diskLock.withLock {
        if (!bufferFile.exists()) return@withLock

        @Suppress("TooGenericExceptionCaught")
        try {
            val json = JSONObject(bufferFile.readText())
            val array = json.optJSONArray(KEY_APP_SESSIONS) ?: return@withLock
            var loaded = 0
            for (i in 0 until array.length()) {
                @Suppress("SwallowedException")
                try {
                    heap.addLast(AppSessionData.fromJson(array.getJSONObject(i)))
                    loaded++
                } catch (e: Exception) {
                    Logger.w("AppSessionBuffer: skipping malformed app session entry at index $i")
                }
            }
            Logger.d("AppSessionBuffer: recovered $loaded app session(s) from disk")
        } catch (ex: Exception) {
            Logger.w("AppSessionBuffer: failed to load from disk — discarding file", ex)
            bufferFile.delete()
        }
    }

    companion object {
        private const val BUFFER_FILENAME = "app-session-buffer.json"
        private const val KEY_APP_SESSIONS = "appSessions"

        /** Default persistence interval: 30 seconds */
        const val DEFAULT_PERSIST_INTERVAL_MS: Long = 30_000L
    }
}
