package com.bugsnag.android.performance

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.bugsnag.android.performance.internal.SpanCategory
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanImpl.Condition

private const val CONDITION_TIMEOUT = 100L

/**
 * A `FrameLayout` that blocks any ViewLoad span from ending until the LoadingIndicatorView has been
 * removed from the View hierarchy. An Activity or Fragment may include any number of
 * LoadingIndicatorViews, and will be considered "loading" as long as at least one remains.
 *
 * Typically the LoadingIndicatorView would be included in your layout xml and removed once all
 * loading is considered to be complete:
 *
 * ```xml
 * <com.bugsnag.android.performance.LoadingIndicatorView
 *     android:id="@+id/loadingIndicator"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     android:gravity="center">
 *
 *     <ProgressBar
 *         android:id="@+id/progressBar"
 *         style="?android:attr/progressBarStyle"
 *         android:layout_width="match_parent"
 *         android:layout_height="wrap_content" />
 * </com.bugsnag.android.performance.LoadingIndicatorView>
 * ```
 *
 * The LoadingIndicatorView can then be removed when loading is complete, either by removing just
 * the LoadingIndicatorView and its content:
 * ```kotlin
 * findViewById<View>(R.id.loadingIndicator).also { loader ->
 *     (loader.parent as ViewGroup).removeView(loader)
 * }
 * ```
 * or by replacing the layout with new content, such as with `setContentView` or replacing a fragment.
 */
public class LoadingIndicatorView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private var condition: Condition?

    init {
        val viewLoad: SpanImpl? = SpanContext.DEFAULT_STORAGE?.currentStack
            ?.filterIsInstance<SpanImpl>()
            ?.find { it.category == SpanCategory.VIEW_LOAD }

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
