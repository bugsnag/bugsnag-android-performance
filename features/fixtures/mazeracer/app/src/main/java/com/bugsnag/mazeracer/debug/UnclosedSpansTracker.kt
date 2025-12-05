package com.bugsnag.mazeracer.debug

import android.util.Log
import com.bugsnag.android.performance.OnSpanEndCallback
import com.bugsnag.android.performance.OnSpanStartCallback
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanContextStorage
import com.bugsnag.android.performance.context.ThreadAwareSpanContextStorage
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.context.ThreadLocalSpanContextStorage
import java.text.SimpleDateFormat
import java.util.Date

private const val TAG = "MazeRacer"

object UnclosedSpansTracker : ThreadAwareSpanContextStorage,
    OnSpanStartCallback,
    OnSpanEndCallback {

    private const val SPAN_INDEX_PADDING = 3
    private const val LOG_LINE_LENGTH = 60

    private val backingStorage = ThreadLocalSpanContextStorage()
    private val openSpans = HashSet<Span>()
    private val discardedSpans = HashMap<Span, DiscardInfo>()
    private val endSpanLog = ArrayList<String>()

    override val currentContext: SpanContext?
        get() = backingStorage.currentContext
    override val currentStack: Sequence<SpanContext>
        get() = backingStorage.currentStack
    override var currentThreadSpanContextStorage: SpanContextStorage?
        get() = backingStorage.currentThreadSpanContextStorage
        set(value) {
            backingStorage.currentThreadSpanContextStorage = value
        }

    override fun attach(spanContext: SpanContext) {
        (spanContext as? Span)?.let { span ->
            onSpanStart(span)
        }

        backingStorage.attach(spanContext)
    }

    override fun clear() {
        backingStorage.clear()
    }

    override fun detach(spanContext: SpanContext) {
        backingStorage.detach(spanContext)

        if ((spanContext as? SpanImpl)?.isDiscarded() == true) {
            synchronized(discardedSpans) {
                discardedSpans.put(
                    spanContext,
                    DiscardInfo(Thread.currentThread().stackTrace.asList()),
                )
            }

            synchronized(openSpans) {
                openSpans.remove(spanContext)
            }
        }
    }

    override fun onSpanStart(span: Span) {
        synchronized(openSpans) {
            openSpans.add(span)
        }
    }

    override fun onSpanEnd(span: Span): Boolean {
        synchronized(openSpans) {
            if (openSpans.remove(span)) {
                endSpanLog.add(span.name)
            }
        }
        return true
    }

    fun dumpUnclosedSpans(scenarioName: String? = null) {
        Log.i(
            TAG,
            buildString {
                append("Unclosed Spans ")

                if (scenarioName != null) {
                    append('[')
                    append(scenarioName)
                    append(']')
                    append(' ')
                }

                repeat(LOG_LINE_LENGTH - length) {
                    append('-')
                }
            },
        )

        synchronized(openSpans) {
            if (openSpans.isEmpty()) {
                Log.i(TAG, "THERE ARE NO UNCLOSED SPANS REMAINING")
            }

            openSpans.forEach { span ->
                dumpSpan(span)
            }

            Log.i(
                TAG,
                "------------------------------------------- Discarded Spans:",
            )

            synchronized(discardedSpans) {
                if (discardedSpans.isEmpty()) {
                    Log.i(TAG, "THERE ARE NO DISCARDED SPANS")
                }

                discardedSpans.forEach { (span, info) ->
                    dumpSpan(span)
                    Log.i(TAG, info.toString())
                }
            }

            Log.i(
                TAG,
                "----------------------------------------- Full Span.end log:",
            )

            endSpanLog.forEachIndexed { index, name ->
                Log.i(TAG, "${index + 1} ${name.padStart(SPAN_INDEX_PADDING)}")
            }
        }

        Log.i(TAG, "--------------------------------------------------- Span log")
    }

    private fun dumpSpan(span: Span) {
        Log.i(
            TAG,
            buildString {
                append(span)

                (span as? SpanImpl)?.let {
                    if (!it.isSampled()) {
                        append(" [not sampled]")
                    } else {
                        append(" [sampled]")
                    }

                    if (!it.isBlocked()) {
                        append(" [not blocked]")
                    } else {
                        append(" [blocked]")
                    }

                    if (!it.isEnded()) {
                        append(" [not ended]")
                    } else {
                        append(" [ended]")
                    }

                    if (!it.isOpen()) {
                        append(" [not open]")
                    } else {
                        append(" [open]")
                    }
                }
            },
        )
    }

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS ")

    internal data class DiscardInfo(
        val origin: List<StackTraceElement>,
        val timestamp: Long = System.currentTimeMillis(),
    ) {
        override fun toString(): String {
            return origin.drop(3).joinToString(
                "<-",
                prefix = dateFormat.format(Date(timestamp)),
                transform = {
                    "${it.className}::${it.methodName}(${it.lineNumber})"
                },
            )
        }
    }

}
