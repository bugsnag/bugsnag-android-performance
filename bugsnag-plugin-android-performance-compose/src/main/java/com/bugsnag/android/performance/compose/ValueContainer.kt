package com.bugsnag.android.performance.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.ViewType

@Immutable
internal data class ValueContainer<T>(var content: T)

private val RENDER_SPAN_OPTIONS = SpanOptions.DEFAULTS
    .makeCurrentContext(false)
    .setFirstClass(false)
private val LocalCompositionSpan =
    compositionLocalOf<ValueContainer<SpanContext?>> { ValueContainer(SpanContext.current) }

@Composable
public fun MeasuredComposable(
    name: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val parentSpanContext: SpanContext? = LocalCompositionSpan.current.content
    val alreadyComposed = remember { ValueContainer(false) }
    val span = if (alreadyComposed.content) {
        null
    } else {
        BugsnagPerformance.startViewLoadSpan(
            ViewType.COMPOSE,
            name,
            SpanOptions.DEFAULTS.within(parentSpanContext),
        )
    }
    alreadyComposed.content = true
    val spanValue = ValueContainer<SpanContext?>(span)
    CompositionLocalProvider(LocalCompositionSpan provides spanValue) {
        Box(
            modifier = modifier then Modifier.drawWithContent {
                if (span?.isEnded() != true) {
                    BugsnagPerformance.startSpan("Draw", RENDER_SPAN_OPTIONS.within(span)).use {
                        drawContent()
                    }
                    span?.end()
                } else {
                    drawContent()
                }
            },
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}
