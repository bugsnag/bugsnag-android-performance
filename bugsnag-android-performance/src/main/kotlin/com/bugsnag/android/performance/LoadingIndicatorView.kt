package com.bugsnag.android.performance

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.bugsnag.android.performance.internal.BugsnagPerformanceInternals
import com.bugsnag.android.performance.internal.SpanCategory
import com.bugsnag.android.performance.internal.SpanContextStack
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanImpl.Condition

private const val CONDITION_TIMEOUT = 100L

public class LoadingIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    internal var condition: Condition?

    init {
        val contextStack: SpanContextStack = BugsnagPerformanceInternals.currentSpanContextStack
        val viewLoad: SpanImpl? = contextStack.current(SpanCategory.VIEW_LOAD)
        condition = viewLoad?.block(CONDITION_TIMEOUT)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        condition?.upgrade()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        condition?.close()
    }
}
