package com.bugsnag.android.performance.internal.appsession

import android.content.Context
import com.bugsnag.android.performance.AppSessionConfig
import com.bugsnag.android.performance.EnabledMetrics
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.internal.BugsnagClock
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.instrumentation.ForegroundState
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * ## Per-app-session immediate delivery
 *
 * Each time an app-session span is closed the controller:
 *   1. Calls `span.end()` so the span enters the Tracer's batch queue.
 *   2. Immediately calls [onAppSessionReady] (wired to `tracer.forceCurrentBatch()`) so the
 *      Worker wakes and sends **that app-session batch right away** — no waiting for the normal
 *      batch timer or size threshold.
 *   3. Stores a typed [AppSessionData] copy in [buffer] (heap memory).
 *      The buffer is periodically persisted to disk by its own scheduler so data survives
 *      process death before delivery.
 *
 * ## Custom app-session names
 *
 * Callers may supply an optional [appSessionName] on `startForegroundAppSessionSpan` /
 * `startBackgroundAppSessionSpan`. It is recorded in [AppSessionData] for local
 * diagnostics and recovery, but intentionally not emitted as a span attribute to keep
 * parity with the iOS app-session payload contract.
 *
 * ## Automatic timeout behaviour
 *
 * | Scenario                                         | Outcome                                                      |
 * |--------------------------------------------------|--------------------------------------------------------------|
 * | Background span open, user never returns         | Auto-closed after [AppSessionConfig.backgroundTimeoutMs]     |
 * |                                                  | with `close_reason = background_timeout`                     |
 * | User returns before timeout fires                | Timeout cancelled; foreground span opens immediately         |
 * | [AppSessionConfig.maxSessionDurationMs] > 0      | Entire session capped; current segment closed with           |
 * |                                                  | `close_reason = session_max_duration`                        |
 */
@Suppress("TooManyFunctions")
internal class AppSessionSpanController
    @Suppress("LongParameterList")
    constructor(
        private val appContext: Context,
        private val spanFactory: SpanFactory,
        private val enabledMetrics: EnabledMetrics = EnabledMetrics(false),
        internal val sessionConfig: AppSessionConfig = AppSessionConfig(),
        private val samplingIntervalMs: Long = DEFAULT_SAMPLING_INTERVAL_MS,
        /**
         * Invoked immediately after each app-session span ends so the delivery layer can flush the span
         * without waiting for the normal batch timer. Wired to `tracer.forceCurrentBatch()` by
         * [BugsnagPerformanceImpl].
         */
        private val onAppSessionReady: (() -> Unit)? = null,
        /**
         * Buffer that holds a typed copy of every completed segment.
         * Periodically persisted to disk so data survives process death.
         */
        private val buffer: AppSessionBuffer? = null,
    ) {
        // ── Session identity ─────────────────────────────────────────────────────
        private var sessionId: String = UUID.randomUUID().toString()
        private val segmentIndex = AtomicInteger(0)

        // ── Active segment state ─────────────────────────────────────────────────
        @Volatile
        private var activeSpan: Span? = null

        @Volatile
        private var activeCollector: AppSessionMetricsCollector? = null

        @Volatile
        private var activeSegmentType: String? = null

        @Volatile
        private var activeSegmentName: String? = null

        @Volatile
        private var activeSegmentStartMs: Long = 0L

        @Volatile
        private var activeSegmentStartUnixNano: Long = 0L

        // ── Timeout scheduler ────────────────────────────────────────────────────
        private val scheduler: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "bugsnag-session-timeout").apply { isDaemon = true }
            }

        init {
            if (sessionConfig.autoStartSession) {
                ForegroundState.addForegroundChangedCallback { inForeground ->
                    if (inForeground) {
                        startForegroundAppSessionSpan()
                    } else {
                        startBackgroundAppSessionSpan()
                    }
                }
                // Start the initial segment based on current state
                if (ForegroundState.isInForeground) {
                    startForegroundAppSessionSpan()
                } else {
                    startBackgroundAppSessionSpan()
                }
            }
        }

        @Volatile
        private var backgroundTimeoutFuture: Future<*>? = null

        @Volatile
        private var maxSessionFuture: Future<*>? = null

        // ─────────────────────────────────────────────────────────────────────────
        // Public API (Unified)
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Starts an app-session segment span (foreground or background).
         * The segment type is determined automatically based on the app's current foreground state.
         *
         * @param appSessionName optional customer-supplied label retained in internal app-session storage.
         */
        fun startAppSessionSpan(appSessionName: String? = null) {
            if (ForegroundState.isInForeground) {
                startForegroundAppSessionSpan(appSessionName)
            } else {
                startBackgroundAppSessionSpan(appSessionName)
            }
        }

        /**
         * Ends the currently active app-session segment span (foreground or background).
         * The close reason will be `client_end_foreground` or `client_end_background` depending on
         * which segment is currently active. No-op if no segment is active.
         */
        fun endAppSessionSpan() {
            when (activeSegmentType) {
                SEGMENT_FOREGROUND -> endForegroundAppSessionSpan()
                SEGMENT_BACKGROUND -> endBackgroundAppSessionSpan()
                else -> {} // no-op if nothing active
            }
        }

        /**
         * Starts a foreground segment span. If a background segment is currently active it is
         * closed immediately (reason: `segment_switched`) and any pending background timeout is
         * cancelled before the new foreground span opens.
         *
         * @param appSessionName optional customer-supplied label retained in internal app-session storage.
         */
        fun startForegroundAppSessionSpan(appSessionName: String? = null) {
            cancelBackgroundTimeout()
            if (activeSegmentType == SEGMENT_BACKGROUND) {
                closeCurrentSegmentSpan(closeReason = "segment_switched")
            }
            if (activeSegmentType != SEGMENT_FOREGROUND || appSessionName != null) {
                openAppSessionSpan(SEGMENT_FOREGROUND, appSessionName)
            }
        }

        /**
         * Ends the active foreground segment span (reason: `client_end`).
         * No-op if no foreground span is active.
         */
        fun endForegroundAppSessionSpan() {
            if (activeSegmentType == SEGMENT_FOREGROUND) {
                closeCurrentSegmentSpan(closeReason = "client_end")
            }
        }

        /**
         * Starts a background segment span. A background-timeout task is scheduled to auto-close
         * this span after [AppSessionConfig.backgroundTimeoutMs] if the user never returns.
         *
         * @param appSessionName optional customer-supplied label retained in internal app-session storage.
         */
        fun startBackgroundAppSessionSpan(appSessionName: String? = null) {
            if (activeSegmentType == SEGMENT_FOREGROUND) {
                closeCurrentSegmentSpan(closeReason = "segment_switched")
            }
            if (activeSegmentType != SEGMENT_BACKGROUND || appSessionName != null) {
                openAppSessionSpan(SEGMENT_BACKGROUND, appSessionName)
                scheduleBackgroundTimeout()
            }
        }

        /**
         * Ends the active background segment span (reason: `client_end`).
         * Cancels any pending background timeout. No-op if no background span is active.
         */
        fun endBackgroundAppSessionSpan() {
            if (activeSegmentType == SEGMENT_BACKGROUND) {
                cancelBackgroundTimeout()
                closeCurrentSegmentSpan(closeReason = "client_end")
            }
        }

        /** Closes any open segment span and shuts down the scheduler and buffer. */
        fun stop() {
            cancelBackgroundTimeout()
            cancelMaxSessionTimeout()
            closeCurrentSegmentSpan(closeReason = "sdk_stopped")
            scheduler.shutdownNow()
            buffer?.stop()
        }

        // ─────────────────────────────────────────────────────────────────────────
        // Timeout scheduling
        // ─────────────────────────────────────────────────────────────────────────

        private fun scheduleBackgroundTimeout() {
            val timeoutMs = sessionConfig.backgroundTimeoutMs
            if (timeoutMs <= 0L) return

            backgroundTimeoutFuture =
                scheduler.schedule(
                    {
                        if (activeSegmentType == SEGMENT_BACKGROUND) {
                            closeCurrentSegmentSpan(closeReason = CLOSE_REASON_BG_TIMEOUT)
                        }
                    },
                    timeoutMs,
                    TimeUnit.MILLISECONDS,
                )
        }

        private fun cancelBackgroundTimeout() {
            backgroundTimeoutFuture?.cancel(false)
            backgroundTimeoutFuture = null
        }

        private fun scheduleMaxSessionTimeout() {
            val capMs = sessionConfig.maxSessionDurationMs
            if (capMs <= 0L) return

            maxSessionFuture =
                scheduler.schedule(
                    {
                        cancelBackgroundTimeout()
                        closeCurrentSegmentSpan(closeReason = CLOSE_REASON_MAX_DURATION)
                    },
                    capMs,
                    TimeUnit.MILLISECONDS,
                )
        }

        private fun cancelMaxSessionTimeout() {
            maxSessionFuture?.cancel(false)
            maxSessionFuture = null
        }

        // ─────────────────────────────────────────────────────────────────────────
        // Segment span helpers
        // ─────────────────────────────────────────────────────────────────────────

        private fun openAppSessionSpan(
            segmentType: String,
            appSessionName: String?,
        ) {
            if (activeSpan != null) {
                closeCurrentSegmentSpan(closeReason = "segment_switched")
            }
            val index = segmentIndex.incrementAndGet()
            val startMs = System.currentTimeMillis()
            val startUnixNano = BugsnagClock.currentUnixNanoTime()

            if (index == 1) scheduleMaxSessionTimeout()

            val spanName =
                if (appSessionName != null) {
                    "[AppSession/$appSessionName]"
                } else {
                    "app_session.$segmentType"
                }

            val span =
                spanFactory.createAppSessionSpan(
                    name = spanName,
                    options =
                        SpanOptions.DEFAULTS
                            .makeCurrentContext(false)
                            .setFirstClass(true),
                ).also { s ->
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        s.setAttribute("bugsnag.session.start_unix_nano", startUnixNano)
                        s.setAttribute("bugsnag.session.start_unix_ms", startMs)
                        if (appSessionName != null) {
                            s.setAttribute("bugsnag.app_session.name", appSessionName)
                        }
                    } catch (_: Exception) {
                        // ignore attribute errors
                    }
                }

            val collector = AppSessionMetricsCollector(appContext, enabledMetrics, samplingIntervalMs)
            collector.start()

            activeSpan = span
            activeCollector = collector
            activeSegmentType = segmentType
            activeSegmentName = appSessionName
            activeSegmentStartMs = startMs
            activeSegmentStartUnixNano = startUnixNano
        }

        @Synchronized
        private fun closeCurrentSegmentSpan(closeReason: String?) {
            val span = activeSpan ?: return
            val collector = activeCollector ?: return
            val appSessionName = activeSegmentName
            val startMs = activeSegmentStartMs
            val startUnixNano = activeSegmentStartUnixNano
            val index = segmentIndex.get()

            // Clear active state immediately so concurrent calls are idempotent
            activeSpan = null
            activeCollector = null
            activeSegmentType = null
            activeSegmentName = null
            activeSegmentStartMs = 0L
            activeSegmentStartUnixNano = 0L

            val metrics = collector.stop()
            attachMetrics(span, metrics)

            val endMs = System.currentTimeMillis()
            val endUnixNano = BugsnagClock.currentUnixNanoTime()

            @Suppress("TooGenericExceptionCaught")
            try {
                closeReason?.let { span.setAttribute("bugsnag.session.close_reason", it) }
                span.setAttribute("bugsnag.session.end_unix_nano", endUnixNano)
                span.setAttribute("bugsnag.session.end_unix_ms", endMs)
                span.setAttribute("bugsnag.session.duration_ms", endMs - startMs)
            } catch (_: Exception) {
                // ignore attribute errors to ensure span.end() is called
            }
            span.end()

            // ── 1. Immediate delivery: wake the Worker to send this segment NOW ──
            @Suppress("TooGenericExceptionCaught")
            try {
                onAppSessionReady?.invoke()
            } catch (_: Exception) {
                // ignore flush errors
            }

            // ── 2. Store typed copy in heap buffer (+ periodic disk persistence) ─
            buffer?.add(
                buildAppSessionData(
                    index = index,
                    appSessionName = appSessionName,
                    startMs = startMs,
                    startUnixNano = startUnixNano,
                    endMs = endMs,
                    endUnixNano = endUnixNano,
                    closeReason = closeReason,
                    metrics = metrics,
                ),
            )
        }

        @Suppress("LongParameterList")
        private fun buildAppSessionData(
            index: Int,
            appSessionName: String?,
            startMs: Long,
            startUnixNano: Long,
            endMs: Long,
            endUnixNano: Long,
            closeReason: String?,
            metrics: AppSessionMetrics,
        ) = AppSessionData(
            sessionId = sessionId,
            index = index,
            appSessionName = appSessionName,
            startTimeMs = startMs,
            startTimeUnixNano = startUnixNano,
            endTimeMs = endMs,
            endTimeUnixNano = endUnixNano,
            durationMs = endMs - startMs,
            closeReason = closeReason ?: "unknown",
            cpuCount = metrics.cpuCount,
            cpuMin = metrics.cpuMin,
            cpuMax = metrics.cpuMax,
            cpuMean = metrics.cpuMean,
            runtimeMemoryCount = metrics.runtimeMemoryCount,
            runtimeMemoryMinBytes = metrics.runtimeMemoryMinBytes,
            runtimeMemoryMaxBytes = metrics.runtimeMemoryMaxBytes,
            runtimeMemoryMeanBytes = metrics.runtimeMemoryMeanBytes,
            deviceMemoryCount = metrics.deviceMemoryCount,
            deviceMemoryMinBytes = metrics.deviceMemoryMinBytes,
            deviceMemoryMaxBytes = metrics.deviceMemoryMaxBytes,
            deviceMemoryMeanBytes = metrics.deviceMemoryMeanBytes,
        )

        // ─────────────────────────────────────────────────────────────────────────
        // Attach aggregated metrics as span attributes
        // ─────────────────────────────────────────────────────────────────────────

        private fun attachMetrics(
            span: Span,
            m: AppSessionMetrics,
        ) {
            if (enabledMetrics.cpu) {
                attachCpuMetrics(span, m)
            }
            if (enabledMetrics.memory) {
                attachMemoryMetrics(span, m)
            }
        }

        private fun attachCpuMetrics(
            span: Span,
            m: AppSessionMetrics,
        ) {
            span.setAttribute("bugsnag.session.cpu.min", m.cpuMin)
            span.setAttribute("bugsnag.session.cpu.max", m.cpuMax)
            span.setAttribute("bugsnag.session.cpu.mean", m.cpuMean)

            span.setAttribute("bugsnag.system.cpu.measures", m.cpuSamples)
            if (m.cpuMainThreadSamples.isNotEmpty()) {
                span.setAttribute("bugsnag.system.cpu.main_thread.measures", m.cpuMainThreadSamples)
                span.setAttribute("bugsnag.system.cpu_min_main_thread", m.cpuMainThreadMin)
                span.setAttribute("bugsnag.system.cpu_max_main_thread", m.cpuMainThreadMax)
                span.setAttribute("bugsnag.system.cpu_mean_main_thread", m.cpuMainThreadMean)
            }
            if (m.cpuOverheadSamples.isNotEmpty()) {
                span.setAttribute("bugsnag.system.cpu.overhead.measures", m.cpuOverheadSamples)
                span.setAttribute("bugsnag.system.cpu_min_overhead", m.cpuOverheadMin)
                span.setAttribute("bugsnag.system.cpu_max_overhead", m.cpuOverheadMax)
                span.setAttribute("bugsnag.system.cpu_mean_overhead", m.cpuOverheadMean)
            }
            if (m.cpuTimestamps.isNotEmpty()) {
                span.setAttribute("bugsnag.system.cpu.timestamps", m.cpuTimestamps)
            }
            span.setAttribute("bugsnag.system.cpu_min_total", m.cpuMin)
            span.setAttribute("bugsnag.system.cpu_max_total", m.cpuMax)
            span.setAttribute("bugsnag.system.cpu_mean_total", m.cpuMean)
        }

        private fun attachMemoryMetrics(
            span: Span,
            m: AppSessionMetrics,
        ) {
            // ── bugsnag.session.* namespace — app-session-only, no conflict ──
            // ── bugsnag.session.* namespace — app-session only, no conflict ──
            span.setAttribute("bugsnag.session.memory.runtime.min", m.runtimeMemoryMinBytes)
            span.setAttribute("bugsnag.session.memory.runtime.max", m.runtimeMemoryMaxBytes)
            span.setAttribute("bugsnag.session.memory.runtime.mean", m.runtimeMemoryMeanBytes)
            span.setAttribute("bugsnag.session.memory.device.min", m.deviceMemoryMinBytes)
            span.setAttribute("bugsnag.session.memory.device.max", m.deviceMemoryMaxBytes)
            span.setAttribute("bugsnag.session.memory.device.mean", m.deviceMemoryMeanBytes)
            // Physical device memory (safe — same value from both writers)
            if (m.deviceMemorySizeBytes > 0) {
                span.setAttribute("bugsnag.device.physical_device_memory", m.deviceMemorySizeBytes)
            }
            // All bugsnag.system.memory.spaces.* keys are owned by MemoryMetricsSource.
            // It runs on span.end() and writes min, max, mean, used, size, timestamps
            // from its own consistent sample window.
        }

        companion object {
            private const val SEGMENT_FOREGROUND = "foreground"
            private const val SEGMENT_BACKGROUND = "background"
            private const val DEFAULT_SAMPLING_INTERVAL_MS = 1_000L

            internal const val CLOSE_REASON_BG_TIMEOUT = "background_timeout"
            internal const val CLOSE_REASON_MAX_DURATION = "session_max_duration"
        }
    }
