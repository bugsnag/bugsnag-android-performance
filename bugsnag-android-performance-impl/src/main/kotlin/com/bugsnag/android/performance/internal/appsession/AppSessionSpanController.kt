package com.bugsnag.android.performance.internal.appsession

import android.app.Application
import android.content.Context
import android.os.Build
import com.bugsnag.android.performance.AppSession
import com.bugsnag.android.performance.AppSessionCallback
import com.bugsnag.android.performance.AppSessionConfig
import com.bugsnag.android.performance.CloseReason
import com.bugsnag.android.performance.EnabledMetrics
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.internal.BugsnagClock
import com.bugsnag.android.performance.internal.Loopers
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.instrumentation.ForegroundState
import com.bugsnag.android.performance.internal.isInForeground
import com.bugsnag.android.performance.internal.processing.ImmutableConfig
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages app-session foreground/background segments, immediate delivery, and timeout-based
 * session lifecycle handling.
 */
@Suppress("TooManyFunctions")
internal class AppSessionSpanController
    @Suppress("LongParameterList")
    constructor(
        private val appContext: Context,
        private val spanFactory: SpanFactory,
        private val enabledMetrics: EnabledMetrics = EnabledMetrics(true),
        internal val sessionConfig: AppSessionConfig = AppSessionConfig(),
        private val samplingIntervalMs: Long = DEFAULT_SAMPLING_INTERVAL_MS,
        /**
         * Invoked immediately after each app-session span ends so the delivery layer can flush the span
         * without waiting for the normal batch timer. Wired to `tracer.forceCurrentBatch()` by
         * `BugsnagPerformanceImpl`.
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
        private var currentSessionState: SessionState? = null

        private val foregroundChangedCallback: (Boolean) -> Unit = { inForeground ->
            if (inForeground) {
                startForegroundAppSessionSpan()
            } else {
                startBackgroundAppSessionSpan()
            }
        }

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
                ForegroundState.addForegroundChangedCallback(foregroundChangedCallback)
                // Start the initial segment based on current state
                val initialInForeground =
                    isInForeground(appContext.applicationContext as? Application)
                        ?: ForegroundState.isInForeground
                if (initialInForeground) {
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
            val resolvedAppSessionName = resolveAppSessionName(appSessionName)
            val wasBackground = activeSegmentType == SEGMENT_BACKGROUND
            cancelBackgroundTimeout()
            if (activeSegmentType == SEGMENT_BACKGROUND) {
                closeCurrentSegmentSpan(closeReason = "segment_switched")
            }
            if (activeSegmentType != SEGMENT_FOREGROUND || activeSegmentName != resolvedAppSessionName) {
                val sessionCreated = openAppSessionSpan(SEGMENT_FOREGROUND, resolvedAppSessionName)
                if (wasBackground && !sessionCreated) {
                    notifySessionForegrounded()
                }
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
            val resolvedAppSessionName = resolveAppSessionName(appSessionName)
            val wasForeground = activeSegmentType == SEGMENT_FOREGROUND
            if (activeSegmentType == SEGMENT_FOREGROUND) {
                closeCurrentSegmentSpan(closeReason = "segment_switched")
            }
            if (activeSegmentType != SEGMENT_BACKGROUND || activeSegmentName != resolvedAppSessionName) {
                val sessionCreated = openAppSessionSpan(SEGMENT_BACKGROUND, resolvedAppSessionName)
                scheduleBackgroundTimeout()
                if (wasForeground && !sessionCreated) {
                    notifySessionBackgrounded()
                }
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
            ForegroundState.removeForegroundChangedCallback(foregroundChangedCallback)
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
        ): Boolean {
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
                    "[AppSession/$segmentType]"
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

            val sessionState = currentSessionState
            if (sessionState == null) {
                currentSessionState = createSessionState(startUnixNano, segmentType)
                notifySessionStarted()
                return true
            }

            sessionState.isInForeground = segmentType == SEGMENT_FOREGROUND
            return false
        }

        private fun resolveAppSessionName(appSessionName: String?): String? {
            return appSessionName ?: sessionConfig.manualSessionDefaultName
        }

        @Synchronized
        private fun closeCurrentSegmentSpan(closeReason: String?) {
            val span = activeSpan ?: return
            val collector = activeCollector ?: return
            val segmentType = activeSegmentType
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

            // Collect metrics before sealing the span; SpanImpl.end() makes the attribute map read-only.
            val metrics = collector.stop()
            attachMetrics(span, metrics)

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

            if (shouldEndSession(closeReason)) {
                finalizeSession(
                    closeReason = closeReason,
                    endUnixNano = endUnixNano,
                    wasForeground = segmentType == SEGMENT_FOREGROUND,
                    index = index,
                )
            }
        }

        private fun shouldEndSession(closeReason: String?): Boolean {
            return closeReason != null && closeReason != "segment_switched"
        }

        private fun finalizeSession(
            closeReason: String?,
            endUnixNano: Long,
            wasForeground: Boolean,
            index: Int,
        ) {
            val sessionState = currentSessionState ?: return
            sessionState.endTimeNano = endUnixNano
            sessionState.closeReason = mapCloseReason(closeReason)
            sessionState.batchIndex = index
            sessionState.isInForeground = wasForeground
            notifySessionEnded()
            currentSessionState = null
            sessionId = UUID.randomUUID().toString()
            segmentIndex.set(0)
        }

        private fun createSessionState(
            startTimeNano: Long,
            segmentType: String,
        ): SessionState {
            return SessionState(
                sessionId = sessionId,
                startTimeNano = startTimeNano,
                appVersion = ImmutableConfig.versionNameFor(appContext) ?: appContext.packageName,
                osVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
                deviceModel = Build.MODEL,
                isInForeground = segmentType == SEGMENT_FOREGROUND,
            )
        }

        private fun notifySessionStarted() {
            dispatchSessionCallback { callback, session -> callback.onSessionStarted(session) }
        }

        private fun notifySessionBackgrounded() {
            dispatchSessionCallback { callback, session -> callback.onSessionBackgrounded(session) }
        }

        private fun notifySessionForegrounded() {
            dispatchSessionCallback { callback, session -> callback.onSessionForegrounded(session) }
        }

        private fun notifySessionEnded() {
            dispatchSessionCallback { callback, session -> callback.onSessionEnded(session) }
        }

        private fun dispatchSessionCallback(callbackAction: (AppSessionCallback, AppSession) -> Unit) {
            val callbacks = sessionConfig.sessionCallbacks.toList()
            if (callbacks.isEmpty()) return

            val sessionSnapshot = currentSessionState?.toAppSession() ?: return
            Loopers.onMainThread {
                callbacks.forEach { callback ->
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        callbackAction(callback, sessionSnapshot)
                    } catch (_: Exception) {
                        // ignore callback failures
                    }
                }
            }
        }

        private fun mapCloseReason(closeReason: String?): CloseReason? {
            return when (closeReason) {
                "client_end" -> CloseReason.CLIENT_END
                CLOSE_REASON_BG_TIMEOUT -> CloseReason.BACKGROUND_TIMEOUT
                else -> null
            }
        }

        private fun SessionState.toAppSession(): AppSession {
            return AppSession.create(
                sessionId = sessionId,
                startTimeNano = startTimeNano,
                appVersion = appVersion,
                osVersion = osVersion,
                deviceModel = deviceModel,
                endTimeNano = endTimeNano,
                closeReason = closeReason,
                batchIndex = batchIndex,
                isInForeground = isInForeground,
            )
        }

        private data class SessionState(
            val sessionId: String,
            val startTimeNano: Long,
            val appVersion: String,
            val osVersion: String,
            val deviceModel: String,
            var endTimeNano: Long? = null,
            var closeReason: CloseReason? = null,
            var batchIndex: Int = 0,
            var isInForeground: Boolean = true,
        )

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
                span.attachAppSessionCpuMetrics(m)
            }
            if (enabledMetrics.memory) {
                span.attachAppSessionMemoryMetrics(m)
            }
        }

        companion object {
            private const val SEGMENT_FOREGROUND = "foreground"
            private const val SEGMENT_BACKGROUND = "background"
            private const val DEFAULT_SAMPLING_INTERVAL_MS = 1_000L

            internal const val CLOSE_REASON_BG_TIMEOUT = "background_timeout"
            internal const val CLOSE_REASON_MAX_DURATION = "session_max_duration"
        }
    }

internal fun Span.attachAppSessionCpuMetrics(m: AppSessionMetrics) {
    val cpuMean = m.cpuMean.coerceIn(m.cpuMin, m.cpuMax)
    val cpuMin = minOf(m.cpuMin, cpuMean)
    val cpuMax = maxOf(m.cpuMax, cpuMean)

    setAttribute("bugsnag.system.cpu_min_total", cpuMin)
    setAttribute("bugsnag.system.cpu_max_total", cpuMax)
    setAttribute("bugsnag.system.cpu_mean_total", cpuMean)

    if (m.cpuSamples.isNotEmpty()) {
        setAttribute("bugsnag.system.cpu_measures_total", m.cpuSamples)
    }

    if (m.cpuMainThreadSamples.isNotEmpty()) {
        val mainThreadMean = m.cpuMainThreadMean.coerceIn(m.cpuMainThreadMin, m.cpuMainThreadMax)
        val mainThreadMin = minOf(m.cpuMainThreadMin, mainThreadMean)
        val mainThreadMax = maxOf(m.cpuMainThreadMax, mainThreadMean)

        setAttribute("bugsnag.system.cpu_measures_main_thread", m.cpuMainThreadSamples)
        setAttribute("bugsnag.system.cpu_min_main_thread", mainThreadMin)
        setAttribute("bugsnag.system.cpu_max_main_thread", mainThreadMax)
        setAttribute("bugsnag.system.cpu_mean_main_thread", mainThreadMean)
    }

    if (m.cpuOverheadSamples.isNotEmpty()) {
        val overheadMean = m.cpuOverheadMean.coerceIn(m.cpuOverheadMin, m.cpuOverheadMax)
        val overheadMin = minOf(m.cpuOverheadMin, overheadMean)
        val overheadMax = maxOf(m.cpuOverheadMax, overheadMean)

        setAttribute("bugsnag.system.cpu_measures_overhead", m.cpuOverheadSamples)
        setAttribute("bugsnag.system.cpu_min_overhead", overheadMin)
        setAttribute("bugsnag.system.cpu_max_overhead", overheadMax)
        setAttribute("bugsnag.system.cpu_mean_overhead", overheadMean)
    }

    if (m.cpuTimestamps.isNotEmpty()) {
        setAttribute("bugsnag.system.cpu_measures_timestamps", m.cpuTimestamps)
    }
}

internal fun Span.attachAppSessionMemoryMetrics(m: AppSessionMetrics) {
    if (m.deviceMemorySizeBytes > 0) {
        setAttribute("bugsnag.device.physical_device_memory", m.deviceMemorySizeBytes)
        setAttribute("bugsnag.system.memory.spaces.device.size", m.deviceMemorySizeBytes)
    }

    if (m.deviceMemorySamplesBytes.isNotEmpty()) {
        setAttribute("bugsnag.system.memory.spaces.device.used", m.deviceMemorySamplesBytes)
    }

    if (m.deviceMemoryCount > 0) {
        val deviceMean = m.deviceMemoryMeanBytes.coerceIn(m.deviceMemoryMinBytes, m.deviceMemoryMaxBytes)
        val deviceMin = minOf(m.deviceMemoryMinBytes, deviceMean)
        val deviceMax = maxOf(m.deviceMemoryMaxBytes, deviceMean)

        setAttribute("bugsnag.system.memory.spaces.device.min", deviceMin)
        setAttribute("bugsnag.system.memory.spaces.device.max", deviceMax)
        setAttribute("bugsnag.system.memory.spaces.device.mean", deviceMean)
    }

    if (m.runtimeMemorySamplesBytes.isNotEmpty()) {
        setAttribute("bugsnag.system.memory.spaces.art.used", m.runtimeMemorySamplesBytes)
    }

    if (m.runtimeMemoryCount > 0) {
        val runtimeMean = m.runtimeMemoryMeanBytes.coerceIn(m.runtimeMemoryMinBytes, m.runtimeMemoryMaxBytes)
        val runtimeMin = minOf(m.runtimeMemoryMinBytes, runtimeMean)
        val runtimeMax = maxOf(m.runtimeMemoryMaxBytes, runtimeMean)

        setAttribute("bugsnag.system.memory.spaces.art.min", runtimeMin)
        setAttribute("bugsnag.system.memory.spaces.art.max", runtimeMax)
        setAttribute("bugsnag.system.memory.spaces.art.mean", runtimeMean)
    }

    if (m.runtimeMemoryTimestamps.isNotEmpty()) {
        setAttribute("bugsnag.system.memory.timestamps", m.runtimeMemoryTimestamps)
    } else if (m.deviceMemoryTimestamps.isNotEmpty()) {
        setAttribute("bugsnag.system.memory.timestamps", m.deviceMemoryTimestamps)
    }
}
