package com.bugsnag.android.performance.internal

import android.os.Handler
import android.os.Message
import com.bugsnag.android.performance.internal.AppStartTracker.Companion.APP_START_TIMEOUT_MS
import com.bugsnag.android.performance.internal.AppStartTracker.Companion.MSG_DISCARD_APP_START

internal class AppStartTracker(
    private val spanTracker: SpanTracker,
    private val spanFactory: SpanFactory,
) : Handler.Callback {
    /**
     * Track whether this is a full Cold start or not. This is `false` by default and is set
     * to `true`
     */
    private var processStarted = false

    internal var isInBackground = true

    private var enabled = true

    private val handler = Loopers.newMainHandler(this)

    public fun onFirstClassLoadReported() {
        if (processStarted) return
        processStarted = true

        val appStart = startAppStartSpan("Cold") ?: return

        spanTracker.associate(appStartToken, AppStartPhase.FRAMEWORK) {
            spanFactory.createAppStartPhaseSpan(AppStartPhase.FRAMEWORK, appStart)
        }
    }

    public fun onApplicationCreate() {
        handler.sendEmptyMessage(MSG_APP_CLASS_COMPLETE)
        spanTracker.endSpan(appStartToken, AppStartPhase.FRAMEWORK)
    }

    public fun onBugsnagPerformanceStart() {
        if (processStarted) return

        onApplicationCreate()

        startAppStartSpan("Cold")
        handler.sendMessageAtFrontOfQueue(handler.obtainMessage(MSG_APP_CLASS_COMPLETE))

        processStarted = true
    }

    internal fun startAppStartSpan(startType: String): SpanImpl? {
        if (enabled) {
            return spanTracker.associate(appStartToken) {
                spanFactory.createAppStartSpan(startType)
            }
        }

        return null
    }

    private fun onApplicationPostCreated() {
        spanTracker.endSpan(appStartToken, AppStartPhase.FRAMEWORK)

        // if the process is being started in the background, we want to discard the AppStart
        handler.sendEmptyMessageDelayed(MSG_DISCARD_APP_START, APP_START_TIMEOUT_MS)
    }

    public fun onActivityCreate(hasSavedInstanceState: Boolean) {
        if (isInBackground) {
            if (hasSavedInstanceState) {
                startAppStartSpan("Hot")
            } else {
                startAppStartSpan("Warm")
            }
        }

        if (spanTracker[appStartToken] == null) {
            // if there is no AppStart span being tracked, we can save a bit of work
            return
        }

        // the app is launching with an Activity, so we remove these messages to ensure
        // that the AppStart span is not discarded for being a background start
        handler.removeMessages(MSG_APP_CLASS_COMPLETE)
        handler.removeMessages(MSG_DISCARD_APP_START)
    }

    public fun onViewLoadComplete(timestamp: Long) {
        // end the AppStart since the app is now considered "visible"
        spanTracker.endAllSpans(appStartToken, timestamp)
    }

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_APP_CLASS_COMPLETE -> onApplicationPostCreated()
            MSG_DISCARD_APP_START -> discardAppStart()
            else -> return false
        }

        return true
    }

    /**
     * Turn off AppStart tracking and discard any in-flight AppStart spans.
     */
    public fun disableAppStartTracking() {
        enabled = false
        discardAppStart()
    }

    public fun discardAppStart() {
        spanTracker.discardAllSpans(appStartToken)
    }

    internal companion object {
        val appStartToken = Any()

        /**
         * Message ID sent directly after opening an AppStart span. This begins a second timeout
         * tracking the time between the end of the `Application.onCreate` and the first
         * `Activity.onCreate` (tracked by [MSG_DISCARD_APP_START]).
         */
        private const val MSG_APP_CLASS_COMPLETE = 1

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
         * How long to wait between the Application class loading and the first Activity.onCreate before
         * discarding the AppStart span (and assuming the app-start is for a Service or Broadcast).
         */
        private const val APP_START_TIMEOUT_MS = 1000L
    }
}
