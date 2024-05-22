package com.bugsnag.android.performance.internal.instrumentation

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.internal.AppStartTracker
import com.bugsnag.android.performance.internal.AutoInstrumentationCache
import com.bugsnag.android.performance.internal.Loopers
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanTracker
import com.bugsnag.android.performance.internal.ViewLoadPhase

/**
 * Message ID used to check whether an Activity is finishing or has been finished at a
 * time we cannot otherwise detect (for example when running on <Q devices we have no
 * onActivityPostCreated hook). The [Message.obj] is expected to be the Activity to check,
 * and if it is finished or finishing all of its associated ViewLoad / ViewLoadPhase
 * spans will be closed.
 */
private const val MSG_CHECK_FINISHED = 1

@Suppress("TooManyFunctions")
internal abstract class AbstractActivityLifecycleInstrumentation(
    protected val spanTracker: SpanTracker,
    protected val spanFactory: SpanFactory,
    protected val startupTracker: AppStartTracker,
    protected val autoInstrumentationCache: AutoInstrumentationCache,
) : Application.ActivityLifecycleCallbacks, Handler.Callback {

    protected val handler = Loopers.newMainHandler(this)

    internal var openLoadSpans: Boolean = true

    internal var closeLoadSpans: Boolean = true

    protected fun autoStartViewLoadSpan(activity: Activity) {
        if (openLoadSpans &&
            autoInstrumentationCache.isInstrumentationEnabled(activity::class.java)
        ) {
            startViewLoadSpan(activity, SpanOptions.DEFAULTS)
        }
    }

    protected fun autoEndViewLoadSpan(activity: Activity) {
        if (closeLoadSpans) {
            endViewLoadSpan(activity)
        } else {
            spanTracker.markSpanAutomaticEnd(activity)
        }
    }

    fun startViewLoadSpan(activity: Activity, spanOptions: SpanOptions): Span {
        val span = spanTracker.associate(activity) {
            // if this is still part of the AppStart span, then the first ViewLoad to end should
            // also end the AppStart span
            if (spanTracker[AppStartTracker.appStartToken] != null &&
                !autoInstrumentationCache.isAppStartActivity(activity::class.java)
            ) {
                spanFactory.createViewLoadSpan(activity, spanOptions) { span ->
                    // we end the AppStart span at the same timestamp as the ViewLoad span ended
                    startupTracker.onViewLoadComplete(
                        (span as? SpanImpl)?.endTime?.get() ?: SystemClock.elapsedRealtimeNanos(),
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

    private fun postCheckActivityFinished(activity: Activity) {
        handler.sendMessage(handler.obtainMessage(MSG_CHECK_FINISHED, activity))
    }

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_CHECK_FINISHED -> (msg.obj as? Activity)?.let { checkActivityFinished(it) }
            else -> return false
        }

        return true
    }

    private fun checkActivityFinished(activity: Activity) {
        if (activity.isFinishing) {
            endViewLoadSpan(activity)
        }
    }

    override fun onActivityDestroyed(p0: Activity) = Unit
    override fun onActivityStopped(activity: Activity)= Unit
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

}

internal class LegacyActivityInstrumentation(
    spanTracker: SpanTracker,
    spanFactory: SpanFactory,
    startupTracker: AppStartTracker,
    autoInstrumentationCache: AutoInstrumentationCache,
) : AbstractActivityLifecycleInstrumentation(
    spanTracker,
    spanFactory,
    startupTracker,
    autoInstrumentationCache,
) {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        startupTracker.onActivityCreate(savedInstanceState != null)
        autoStartViewLoadSpan(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        autoEndViewLoadSpan(activity)
    }

}

internal class ActivityLifecycleInstrumentation(
    spanTracker: SpanTracker,
    spanFactory: SpanFactory,
    startupTracker: AppStartTracker,
    autoInstrumentationCache: AutoInstrumentationCache,
) : AbstractActivityLifecycleInstrumentation(
    spanTracker,
    spanFactory,
    startupTracker,
    autoInstrumentationCache,
) {

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        startupTracker.onActivityCreate(savedInstanceState != null)
        autoStartViewLoadSpan(activity)
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
        autoEndViewLoadSpan(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        super.onActivityStopped(activity)
        Loopers.mainHandler.post { spanTracker.discardAllSpans(activity) }
    }

    private fun startViewLoadPhase(activity: Activity, phase: ViewLoadPhase) {
        val viewLoadSpan = spanTracker[activity]
        if (openLoadSpans && viewLoadSpan != null &&
            autoInstrumentationCache.isInstrumentationEnabled(activity::class.java)
        ) {
            spanTracker.associate(activity, phase) {
                spanFactory.createViewLoadPhaseSpan(
                    activity,
                    phase,
                    SpanOptions.within(viewLoadSpan),
                )
            }
        }
    }

    private fun endViewLoadPhase(activity: Activity, phase: ViewLoadPhase) {
        spanTracker.endSpan(activity, phase)
    }

}
