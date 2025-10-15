package com.bugsnag.android.performance.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.internal.SpanCategory
import com.bugsnag.android.performance.internal.SpanImpl

private const val CONDITION_TIMEOUT = 100L

/**
 * A LoadingIndicator is an invisible composable that will block any ViewLoad span from ending
 * until the LoadingIndicator has been disposed. This provides an easy and effective way to
 * accurately measure the time-to-interactivity for any Activity, Fragment or composable (when
 * using [MeasuredComposable]).
 *
 * A screen may include any number of LoadingIndicators, and the screen is considered to be
 * "loading" as long as at least one remains active.
 *
 * Typically this composable can wrap or be embedded within the content of a loading placeholder or
 * loading status indicator. For example:
 *
 * ```kotlin
 * if (loading) {
 *   LoadingIndicator {
 *     CircularProgressIndicator()
 *   }
 * }
 *
 * // LoadingIndicator can also be embedded within a placeholder composable
 * @Composable
 * fun UserProfilePlaceholder() {
 *   LoadingIndicator()
 *   // other placeholder content
 * }
 * ```
 *
 * @param modifier any modifier to be passed down to the Box that will wrap [content]
 * @param content option content for this LoadingIndicator
 * @param spanName optional name for a Span that will be opened for the duration of this LoadingIndicator
 */
@Composable
public fun LoadingIndicator(
    modifier: Modifier = Modifier,
    spanName: String? = null,
    content: (@Composable BoxScope.() -> Unit)? = null,
) {
    DisposableEffect(spanName) {
        val viewLoad: SpanImpl? =
            SpanContext.defaultStorage?.currentStack
                ?.filterIsInstance<SpanImpl>()
                ?.find { it.category == SpanCategory.VIEW_LOAD }

        val condition = viewLoad?.block(CONDITION_TIMEOUT)
        val spanContext = condition?.upgrade()

        val loadingSpan =
            if (spanName != null && spanContext != null) {
                BugsnagPerformance.startSpan(spanName, loadingSpanOptions.within(spanContext))
            } else {
                null
            }

        onDispose {
            condition?.close()
            loadingSpan?.close()
        }
    }

    if (content != null) {
        Box(modifier) {
            content()
        }
    }
}

private val loadingSpanOptions = SpanOptions.setFirstClass(false)
