package com.bugsnag.android.performance.compose

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.os.Bundle
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.internal.SpanCategory
import com.bugsnag.android.performance.internal.SpanImpl
import java.util.Collections
import java.util.WeakHashMap

public object ComposeActivityLifecycleCallbacks : ActivityLifecycleCallbacks {
    private const val VIEW_LOAD_BLOCKING_TIMEOUT = 500L

    private val viewLoadConditions =
        Collections.synchronizedMap(WeakHashMap<Activity, ViewLoad>())

    internal fun createCondition(context: Context): SpanImpl.Condition? {
        return viewLoadConditions[context]?.createCondition()
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        val viewLoadSpan: SpanImpl = SpanContext.DEFAULT_STORAGE
            ?.currentStack
            ?.filterIsInstance<SpanImpl>()
            ?.find { it.category == SpanCategory.VIEW_LOAD }
            ?: return

        val blockingCondition =
            viewLoadSpan.block(VIEW_LOAD_BLOCKING_TIMEOUT)
                ?: return // if we can't block the span, return it

        viewLoadConditions[activity] = ViewLoad(viewLoadSpan, blockingCondition)
    }

    override fun onActivityStarted(activity: Activity): Unit = Unit

    override fun onActivityResumed(activity: Activity): Unit = Unit

    override fun onActivityPaused(activity: Activity): Unit = Unit

    override fun onActivityStopped(activity: Activity): Unit = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ): Unit = Unit

    override fun onActivityDestroyed(activity: Activity): Unit = Unit

    internal data class ViewLoad(
        val viewLoadSpan: SpanImpl,
        private var blockingCondition: SpanImpl.Condition?,
    ) {
        fun createCondition(): SpanImpl.Condition? {
            val condition = blockingCondition ?: viewLoadSpan.block(VIEW_LOAD_BLOCKING_TIMEOUT)
            blockingCondition = null
            return condition
        }
    }
}
