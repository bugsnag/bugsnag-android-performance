package com.bugsnag.android.performance.internal

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.internal.InstrumentedAppState.Companion.applicationToken
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanOptions
import kotlin.math.max

typealias InForegroundCallback = (inForeground: Boolean) -> Unit

class PerformanceLifecycleCallbacks internal constructor(
    private val spanTracker: SpanTracker,
    private val spanFactory: SpanFactory,
    private val inForegroundCallback: InForegroundCallback,
) : ActivityLifecycleCallbacks, Handler.Callback {

    private val handler = Handler(Looper.getMainLooper(), this)

    /**
     * The number of Activities that have been created but not destroyed. This is used to determine
     * whether to treat an `Activity.onCreate` as an "app launch".
     */
    private var activityInstanceCount = 0

    /**
     * The number of Activities that have been started but not stopped. This is used to determine
     * whether the app is considered "in foreground" or not.
     */
    private var startedActivityCount = 0

    private var backgroundSent = true

    internal var openLoadSpans: Boolean = false

    internal var closeLoadSpans: Boolean = false

    internal var instrumentAppStart: Boolean = true

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            appStartIsForeground()
            maybeStartViewLoad(activity, savedInstanceState)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++

        if (startedActivityCount == 1 && backgroundSent) {
            inForegroundCallback(true)
        } else {
            handler.removeMessages(MSG_SEND_BACKGROUND)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            endViewLoad(activity)
        }
    }

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        appStartIsForeground()
        maybeStartViewLoad(activity, savedInstanceState)
        startViewLoadPhase(activity, ViewLoadPhase.CREATE)
    }

    override fun onActivityPreStarted(activity: Activity) {
        endViewLoadPhase(activity, ViewLoadPhase.CREATE)
        startViewLoadPhase(activity, ViewLoadPhase.START)
    }

    override fun onActivityPreResumed(activity: Activity) {
        endViewLoadPhase(activity, ViewLoadPhase.START)
        startViewLoadPhase(activity, ViewLoadPhase.RESUME)
    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        endViewLoadPhase(activity, ViewLoadPhase.CREATE)
    }

    override fun onActivityPostStarted(activity: Activity) {
        endViewLoadPhase(activity, ViewLoadPhase.START)
    }

    override fun onActivityPostResumed(activity: Activity) {
        endViewLoadPhase(activity, ViewLoadPhase.RESUME)
        endViewLoad(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = max(0, startedActivityCount - 1)

        if (startedActivityCount == 0) {
            backgroundSent = false
            handler.sendEmptyMessageDelayed(MSG_SEND_BACKGROUND, BACKGROUND_TIMEOUT_MS)
        }

        handleViewLoadLeak(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        // make sure we never drop below 0, this can happen if BugsnagPerformance.start was
        // called *after* the first Activity was started
        activityInstanceCount = max(0, activityInstanceCount - 1)

        // while leak detection usually triggers in onActivityStopped, if an Activity calls finish()
        // from it's onCreate method then it is never started (and so never gets stopped), we handle
        // those cases from here
        handleViewLoadLeak(activity)
    }

    private fun handleViewLoadLeak(activity: Activity) {
        if (spanTracker.markSpanLeaked(activity)) {
            Logger.w(
                "${activity::class.java.name} appears to have leaked a ViewLoad Span. " +
                        "This is probably because BugsnagPerformance.endViewLoad was not called.",
            )
        }
    }

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_SEND_BACKGROUND -> sendBackgroundCallback()
            MSG_APP_CLASS_COMPLETE -> onApplicationPostCreated()
            MSG_CHECK_FINISHED -> (msg.obj as? Activity)?.let { checkActivityFinished(it) }
            MSG_DISCARD_APP_START -> discardAppStart()

            else -> return false
        }

        return true
    }

    private fun checkActivityFinished(activity: Activity) {
        if (activity.isFinishing) {
            endViewLoad(activity)
        }
    }

    private fun onApplicationPostCreated() {
        spanTracker.endSpan(applicationToken, AppStartPhase.APPLICATION_INIT)
        handler.sendEmptyMessageDelayed(MSG_DISCARD_APP_START, APP_START_TIMEOUT_MS)
    }

    private fun sendBackgroundCallback() {
        inForegroundCallback(false)
        backgroundSent = true
        discardAppStart()
    }

    fun startAppStartSpan(startType: String) {
        if (spanTracker[applicationToken] == null && instrumentAppStart) {
            spanTracker.associate(applicationToken) {
                spanFactory.createAppStartSpan(startType)
            }
        }
    }

    fun startAppStartPhase(appStartPhase: AppStartPhase) {
        val appStartSpan = spanTracker[applicationToken]

        if (appStartSpan != null && instrumentAppStart) {
            spanTracker.associate(applicationToken, appStartPhase) {
                spanFactory.createAppStartPhaseSpan(appStartPhase, appStartSpan)
            }
        }
    }

    fun bugsnagPerformanceStart(instrumentAppStart: Boolean) {
        if (!instrumentAppStart) {
            discardAppStart()
        } else if (activityInstanceCount == 0) {
            // we mark the Application.onCreate ended as the very-next message
            // sendMessageAtFrontOfQueue is important as other transactions will already be
            // on the queue at this point, and we don't want to measure them
            handler.sendMessageAtFrontOfQueue(handler.obtainMessage(MSG_APP_CLASS_COMPLETE))
            startAppStartSpan("Cold")
        }
    }

    fun discardAppStart() {
        // we discard all spans related to AppStart
        spanTracker.removeAllAssociations(applicationToken).forEach { it.discard() }
    }

    private fun maybeStartAppLoad(savedInstanceState: Bundle?) {
        if (activityInstanceCount == 0) {
            if (savedInstanceState == null) {
                startAppStartSpan("Warm")
            } else {
                startAppStartSpan("Hot")
            }
        }
    }

    private fun maybeEndAppStartSpan(endTime: Long) {
        if (instrumentAppStart) {
            spanTracker.endAllSpans(applicationToken, endTime)
        }

        // we may have an appStartupSpan from before the configuration was in-place
        spanTracker.removeAssociation(applicationToken)
    }

    /**
     * Called when it becomes clear that any pending AppStart is in the foreground, so we should
     * avoid potentially discarding the AppStart span (and remove our background app-start detection).
     */
    private fun appStartIsForeground() {
        handler.removeMessages(MSG_APP_CLASS_COMPLETE)
        handler.removeMessages(MSG_DISCARD_APP_START)
    }

    private fun maybeStartViewLoad(activity: Activity, savedInstanceState: Bundle?) {
        try {
            maybeStartAppLoad(savedInstanceState)

            if (openLoadSpans) {
                startViewLoadSpan(activity, SpanOptions.DEFAULTS)
            }
        } finally {
            activityInstanceCount++
        }
    }

    fun startViewLoadSpan(activity: Activity, spanOptions: SpanOptions): Span {
        val span = spanTracker.associate(activity) {
            // if this is still part of the AppStart span, then the first ViewLoad to end should
            // also end the AppStart span
            if (appStartSpan != null) {
                spanFactory.createViewLoadSpan(activity, spanOptions) { span ->
                    // we end the AppStart span at the same timestamp as the ViewLoad span ended
                    maybeEndAppStartSpan(
                        (span as? SpanImpl)?.endTime ?: SystemClock.elapsedRealtimeNanos(),
                    )

                    BugsnagPerformance.tracer.onEnd(span)
                }
            } else {
                spanFactory.createViewLoadSpan(activity, spanOptions)
            }

        }

        handler.sendMessage(handler.obtainMessage(MSG_CHECK_FINISHED, activity))

        return span
    }

    private fun endViewLoad(activity: Activity) {
        if (closeLoadSpans) {
            // close any pending spans associated with the Activity
            spanTracker.endAllSpans(activity)
        } else {
            spanTracker.markSpanAutomaticEnd(activity)
        }

        // if we do not automatically open spans, we always close the AppStart span when we
        // would end ViewLoad for an Activity
        if (!openLoadSpans) {
            maybeEndAppStartSpan(SystemClock.elapsedRealtimeNanos())
        }
    }

    private fun startViewLoadPhase(activity: Activity, phase: ViewLoadPhase) {
        val viewLoadSpan = spanTracker[activity]
        if (openLoadSpans && viewLoadSpan != null) {
            spanTracker.associate(activity, phase) {
                spanFactory.createViewLoadPhaseSpan(activity, phase, viewLoadSpan)
            }
        }
    }

    private fun endViewLoadPhase(activity: Activity, phase: ViewLoadPhase) {
        spanTracker.endSpan(activity, phase)
    }

    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private companion object {
        private const val MSG_SEND_BACKGROUND = 1

        /**
         * Message ID sent directly after opening an AppStart span. This begins a second timeout
         * tracking the time between the end of the `Application.onCreate` and the first
         * `Activity.onCreate` (tracked by [MSG_DISCARD_APP_START]).
         */
        private const val MSG_APP_CLASS_COMPLETE = 2

        /**
         * Message ID sent delayed (after [APP_START_TIMEOUT_MS]) to force-discard any AppStart
         * span still in-progress. This is used to avoid skewing metrics due to background starts
         * triggered by things like broadcasts, service starts or content providers being accessed.
         *
         * This message is negated by any `Activity.onCreate` which will
         * `removeMessages(MSG_DISCARD_APP_START)`.
         */
        private const val MSG_DISCARD_APP_START = 3

        /**
         * Message ID used to check whether an Activity is finishing or has been finished at a
         * time we cannot otherwise detect (for example when running on <Q devices we have no
         * onActivityPostCreated hook). The [Message.obj] is expected to be the Activity to check,
         * and if it is finished or finishing all of its associated ViewLoad / ViewLoadPhase
         * spans will be closed.
         */
        private const val MSG_CHECK_FINISHED = 4

        /**
         * Same as `androidx.lifecycle.ProcessLifecycleOwner` and is used to avoid reporting
         * background / foreground changes when there is only 1 Activity being restarted for configuration
         * changes.
         */
        private const val BACKGROUND_TIMEOUT_MS = 700L

        /**
         * How long to wait between the Application class loading and the first Activity.onCreate before
         * discarding the AppStart span (and assuming the app-start is for a Service or Broadcast).
         */
        private const val APP_START_TIMEOUT_MS = 1000L
    }
}
