package com.bugsnag.android.performance.internal

import android.app.Activity
import android.app.Application
import android.os.SystemClock
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.EnabledMetrics
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.NetworkRequestInfo
import com.bugsnag.android.performance.NetworkRequestInstrumentationCallback
import com.bugsnag.android.performance.OnSpanStartCallback
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.SpanMetrics
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.ViewType
import com.bugsnag.android.performance.internal.context.ThreadLocalSpanContextStorage
import com.bugsnag.android.performance.internal.integration.NotifierIntegration
import com.bugsnag.android.performance.internal.metrics.MetricsContainer
import com.bugsnag.android.performance.internal.processing.AttributeLimits
import com.bugsnag.android.performance.internal.processing.SpanTaskWorker
import com.bugsnag.android.performance.internal.util.Prioritized
import com.bugsnag.android.performance.internal.util.PrioritizedSet
import java.util.UUID

internal typealias AttributeSource = (target: SpanImpl) -> Unit

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SpanFactory internal constructor(
    public var spanProcessor: SpanProcessor,
    public val spanAttributeSource: AttributeSource,
    private val spanTaskWorker: SpanTaskWorker = SpanTaskWorker(),
    metricsContainer: MetricsContainer? = null,
) {
    public var networkRequestCallback: NetworkRequestInstrumentationCallback? = null

    public var sampler: Sampler? = null

    internal var attributeLimits: AttributeLimits? = null

    internal val spanStartCallbacks = PrioritizedSet<OnSpanStartCallback>()

    internal val metricsContainer: MetricsContainer =
        metricsContainer
            ?: MetricsContainer(spanTaskWorker, this)

    public constructor(
        spanProcessor: SpanProcessor,
        spanAttributeSource: AttributeSource = {},
    ) : this(
        spanProcessor,
        spanAttributeSource,
        SpanTaskWorker(),
    )

    init {
        if (SpanContext.defaultStorage == null) {
            SpanContext.Storage.defaultStorage = ThreadLocalSpanContextStorage()
        }
    }

    internal fun attach(application: Application) {
        metricsContainer.attach(application)
        spanTaskWorker.start()
    }

    internal fun configure(
        spanProcessor: SpanProcessor,
        attributeLimits: AttributeLimits,
        spanStartCallbacks: Collection<Prioritized<OnSpanStartCallback>>,
        networkRequestCallback: NetworkRequestInstrumentationCallback?,
        enabledMetrics: EnabledMetrics,
    ) {
        this.spanProcessor = spanProcessor
        this.attributeLimits = attributeLimits
        this.networkRequestCallback = networkRequestCallback

        this.spanStartCallbacks.addAll(spanStartCallbacks)

        metricsContainer.configure(enabledMetrics)
    }

    @JvmOverloads
    public fun createCustomSpan(
        name: String,
        options: SpanOptions = SpanOptions.DEFAULTS,
        spanProcessor: SpanProcessor = this.spanProcessor,
    ): SpanImpl {
        return createSpan(
            name,
            SpanKind.INTERNAL,
            SpanCategory.CUSTOM,
            options.startTime,
            options.parentContext,
            options.isFirstClass != false,
            options.makeContext,
            options.spanMetrics,
            spanProcessor,
        )
    }

    public fun createNetworkSpan(
        url: String,
        verb: String,
        options: SpanOptions = SpanOptions.DEFAULTS,
        spanProcessor: SpanProcessor = this.spanProcessor,
    ): SpanImpl? {
        val reqInfo = NetworkRequestInfo(url)
        networkRequestCallback?.onNetworkRequest(reqInfo)
        reqInfo.url?.let { resultUrl ->
            val verbUpper = verb.uppercase()
            val span =
                createSpan(
                    "[HTTP/$verbUpper]",
                    SpanKind.CLIENT,
                    SpanCategory.NETWORK,
                    options.startTime,
                    options.parentContext,
                    options.isFirstClass,
                    options.makeContext,
                    options.spanMetrics,
                    spanProcessor,
                )
            span.attributes["http.url"] = resultUrl
            span.attributes["http.method"] = verbUpper
            return span
        }
        return null
    }

    public fun createViewLoadSpan(
        activity: Activity,
        options: SpanOptions = SpanOptions.DEFAULTS,
        spanProcessor: SpanProcessor = this.spanProcessor,
    ): SpanImpl {
        val activityName = activity::class.java.simpleName
        return createViewLoadSpan(ViewType.ACTIVITY, activityName, options, spanProcessor)
    }

    public fun createViewLoadSpan(
        viewType: ViewType,
        viewName: String,
        options: SpanOptions = SpanOptions.DEFAULTS,
        spanProcessor: SpanProcessor = this.spanProcessor,
    ): SpanImpl {
        val isFirstClass = options.isFirstClass ?: defaultIsFirstClassViewLoad()

        val span =
            createSpan(
                "[ViewLoad/${viewType.spanName}]$viewName",
                SpanKind.INTERNAL,
                SpanCategory.VIEW_LOAD,
                options.startTime,
                options.parentContext,
                options.isFirstClass,
                options.makeContext,
                options.spanMetrics,
                spanProcessor,
            )

        span.attributes["bugsnag.view.type"] = viewType.typeName
        span.attributes["bugsnag.view.name"] = viewName
        span.attributes["bugsnag.span.first_class"] = isFirstClass

        val appStart =
            SpanContext.defaultStorage
                ?.currentStack
                ?.filterIsInstance<SpanImpl>()
                ?.find { it.category == SpanCategory.APP_START }

        if (appStart != null && appStart.attributes["bugsnag.app_start.first_view_name"] == null) {
            appStart.attributes["bugsnag.view.type"] = viewType.typeName
            appStart.attributes["bugsnag.app_start.first_view_name"] = viewName
        }

        return span
    }

    private fun defaultIsFirstClassViewLoad(): Boolean {
        return SpanContext.defaultStorage
            ?.currentStack
            ?.filterIsInstance<SpanImpl>()
            ?.filter { it.category == SpanCategory.VIEW_LOAD }
            ?.none() == true
    }

    public fun createViewLoadPhaseSpan(
        viewName: String,
        viewType: ViewType,
        phase: ViewLoadPhase,
        options: SpanOptions,
        spanProcessor: SpanProcessor = this.spanProcessor,
    ): SpanImpl {
        val phaseName = phase.phaseNameFor(viewType)
        val span =
            createSpan(
                "[ViewLoadPhase/$phaseName]$viewName",
                SpanKind.INTERNAL,
                SpanCategory.VIEW_LOAD_PHASE,
                options.startTime,
                options.parentContext,
                options.isFirstClass,
                options.makeContext,
                options.spanMetrics,
                spanProcessor,
            )

        span.attributes["bugsnag.view.name"] = viewName
        span.attributes["bugsnag.view.type"] = viewType.typeName
        span.attributes["bugsnag.phase"] = phaseName

        return span
    }

    public fun createViewLoadPhaseSpan(
        activity: Activity,
        phase: ViewLoadPhase,
        options: SpanOptions,
        spanProcessor: SpanProcessor = this.spanProcessor,
    ): SpanImpl {
        return createViewLoadPhaseSpan(
            activity::class.java.simpleName,
            ViewType.ACTIVITY,
            phase,
            options,
            spanProcessor,
        )
    }

    public fun createAppStartSpan(
        startType: String,
        spanProcessor: SpanProcessor = this.spanProcessor,
    ): SpanImpl {
        val span =
            createSpan(
                "[AppStart/Android$startType]",
                SpanKind.INTERNAL,
                SpanCategory.APP_START,
                SystemClock.elapsedRealtimeNanos(),
                null,
                isFirstClass = true,
                makeContext = true,
                SpanMetrics(rendering = true),
                spanProcessor,
            )

        span.attributes["bugsnag.app_start.type"] = startType.lowercase()

        return span
    }

    public fun createAppStartPhaseSpan(
        phase: AppStartPhase,
        appStartContext: SpanContext,
        spanProcessor: SpanProcessor = this.spanProcessor,
    ): SpanImpl {
        val span =
            createSpan(
                "[AppStartPhase/${phase.phaseName}]",
                SpanKind.INTERNAL,
                SpanCategory.APP_START_PHASE,
                SystemClock.elapsedRealtimeNanos(),
                appStartContext,
                isFirstClass = false,
                makeContext = true,
                null,
                spanProcessor,
            )

        span.attributes["bugsnag.phase"] = "FrameworkLoad"

        return span
    }

    private fun createSpan(
        name: String,
        kind: SpanKind,
        category: SpanCategory,
        startTime: Long,
        parentContext: SpanContext?,
        isFirstClass: Boolean?,
        makeContext: Boolean,
        spanMetrics: SpanMetrics?,
        spanProcessor: SpanProcessor,
    ): SpanImpl {
        val parent = parentContext?.takeIf { it.traceId.isValidTraceId() }

        val metrics = metricsContainer.createSpanMetricsSnapshot(isFirstClass == true, spanMetrics)
        val span =
            SpanImpl(
                name = name,
                category = category,
                kind = kind,
                startTime = startTime,
                traceId = parent?.traceId ?: UUID.randomUUID(),
                parentSpanId = parent?.spanId ?: 0L,
                makeContext = makeContext,
                attributeLimits = attributeLimits,
                metrics = metrics,
                processor = spanProcessor,
                timeoutExecutor = spanTaskWorker,
            )

        if (isFirstClass != null) {
            span.attributes["bugsnag.span.first_class"] = isFirstClass
        }

        span.samplingProbability = sampler?.sampleProbability ?: 1.0

        spanAttributeSource(span)

        runOnSpanStartCallbacks(span)
        NotifierIntegration.onSpanStarted(span)

        return span
    }

    private fun runOnSpanStartCallbacks(span: SpanImpl) {
        @Suppress("TooGenericExceptionCaught")
        spanStartCallbacks.forEach {
            try {
                it.onSpanStart(span)
            } catch (ex: Exception) {
                // swallow any exceptions to avoid any possible crash
                Logger.w("Exception in onSpanStart callback", ex)
            }
        }
    }

    private fun UUID.isValidTraceId() = mostSignificantBits != 0L || leastSignificantBits != 0L
}
