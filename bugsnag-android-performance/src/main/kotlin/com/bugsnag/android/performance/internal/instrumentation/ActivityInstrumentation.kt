package com.bugsnag.android.performance.internal.instrumentation

import android.app.Activity
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.internal.AutoInstrumentationCache
import com.bugsnag.android.performance.internal.Loopers
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanTracker
import com.bugsnag.android.performance.internal.ViewLoadPhase

internal class ActivityInstrumentation(
    private val spanTracker: SpanTracker,
    private val spanFactory: SpanFactory,
    private val startInstrumentation: AppStartInstrumentation,
    private val autoInstrumentationCache: AutoInstrumentationCache,
) : Handler.Callback {

    private val handler = Loopers.newMainHandler(this)

    fun autoStartViewLoadSpan(activity: Activity) {
        if (autoInstrumentationCache.shouldAutoStartSpan(activity)) {
            startViewLoadSpan(activity, SpanOptions.DEFAULTS)
        }
    }

    fun autoEndViewLoadSpan(activity: Activity) {
        if (autoInstrumentationCache.shouldAutoEndSpan(activity)) {
            endViewLoadSpan(activity)
        } else {
            spanTracker.markSpanAutomaticEnd(activity)
        }
    }

    fun startViewLoadSpan(activity: Activity, spanOptions: SpanOptions): Span {
        val span = spanTracker.associate(activity) {
            // if this is still part of the AppStart span, then the first ViewLoad to end should
            // also end the AppStart span
            if (startInstrumentation.isAppStartActive() &&
                !autoInstrumentationCache.isAppStartActivity(activity::class.java)
            ) {
                spanFactory.createViewLoadSpan(activity, spanOptions) { span ->
                    // we end the AppStart span at the same timestamp as the ViewLoad span ended
                    startInstrumentation.onViewLoadComplete(
                        (span as? SpanImpl)?.endTime ?: SystemClock.elapsedRealtimeNanos(),
                    )
                    spanFactory.spanProcessor.onEnd(span)
                }
            } else {
                spanFactory.createViewLoadSpan(activity, spanOptions)
            }
        }

        postCheckActivityFinished(activity)

        return span
    }

    fun endViewLoadSpan(activity: Activity) {
        spanTracker.endSpan(activity)
    }

    fun postCheckActivityFinished(activity: Activity) {
        handler.sendMessage(handler.obtainMessage(MSG_CHECK_FINISHED, activity))
    }

    fun postDiscardViewLoad(activity: Activity) {
        handler.sendMessageDelayed(
            Message.obtain(
                handler,
                MSG_DISCARD_VIEW_LOAD,
                activity,
            ),
            ForegroundState.BACKGROUND_TIMEOUT_MS,
        )
    }

    fun cancelDiscardViewLoads() {
        handler.removeMessages(MSG_DISCARD_VIEW_LOAD)
    }

    fun startViewLoadPhase(activity: Activity, phase: ViewLoadPhase) {
        val viewLoadSpan = spanTracker[activity]
        if (viewLoadSpan != null && autoInstrumentationCache.shouldAutoStartSpan(activity)) {
            spanTracker.associate(activity, phase) {
                spanFactory.createViewLoadPhaseSpan(
                    activity,
                    phase,
                    SpanOptions.within(viewLoadSpan),
                )
            }
        }
    }

    fun endViewLoadPhase(activity: Activity, phase: ViewLoadPhase) {
        spanTracker.endSpan(activity, phase)
    }

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_CHECK_FINISHED -> {
                (msg.obj as? Activity)?.let { checkActivityFinished(it) }
            }

            MSG_DISCARD_VIEW_LOAD -> {
                spanTracker.discardAllSpans(msg.what)
                startInstrumentation.discardAppStart()
            }

            else -> return false
        }

        return true
    }

    private fun checkActivityFinished(activity: Activity) {
        if (activity.isFinishing) {
            endViewLoadSpan(activity)
        }
    }

    companion object {
        /**
         * Message ID used to check whether an Activity is finishing or has been finished at a
         * time we cannot otherwise detect (for example when running on <Q devices we have no
         * onActivityPostCreated hook). The [Message.obj] is expected to be the Activity to check,
         * and if it is finished or finishing all of its associated ViewLoad / ViewLoadPhase
         * spans will be closed.
         */
        private const val MSG_CHECK_FINISHED = 1

        private const val MSG_DISCARD_VIEW_LOAD = 2
    }
}
