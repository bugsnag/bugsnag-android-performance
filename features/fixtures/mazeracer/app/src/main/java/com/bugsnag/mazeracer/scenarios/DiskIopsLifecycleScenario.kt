package com.bugsnag.mazeracer.scenarios

import android.app.Activity
import android.content.Intent
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.PerformanceTestUtils
import com.bugsnag.mazeracer.Scenario
import com.bugsnag.mazeracer.log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ROAD 2233 Scenario 9: disk IOPS still collected across real lifecycle transitions
 * for custom and app_session spans. Termination-without-end is not Maze-deliverable
 * (the span never completes).
 */
class DiskIopsLifecycleScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    private var customSpan: Span? = null

    private val spanType: String
        get() = scenarioConfig["span_type"] ?: "custom"

    private val transition: String
        get() = scenarioConfig["transition"] ?: scenarioMetadata

    private val isAppSession: Boolean
        get() = spanType == "app_session"

    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 1
        InternalDebug.attachDiskIoSnapshots = true
        config.appSessionConfig.autoStartSession = false
        config.enabledMetrics.disk = true
    }

    override fun startScenario() {
        DiskIopsSupport.ensureProcIoReadable(context)
        BugsnagPerformance.start(config)
        forceConfigureMetrics(config.enabledMetrics)

        when (transition) {
            "ends_in_background" -> endSpanInBackground()
            "starts_in_background" -> startSpanInBackground()
            else -> midSpanBackgroundThenForeground()
        }
    }

    private fun endSpanInBackground() {
        startLifecycleSpan()
        DiskIopsSupport.generateDiskActivity(context, "lifecycle-fg")
        runOnceOnStop {
            DiskIopsSupport.generateDiskActivity(context, "lifecycle-bg")
            Thread.sleep(DiskIopsSupport.SPAN_SLEEP_MS)
            finishSpan()
        }
        sendToHome()
    }

    private fun startSpanInBackground() {
        runOnceOnStop {
            startLifecycleSpan()
            DiskIopsSupport.generateDiskActivity(context, "lifecycle-bg-start")
            Thread.sleep(DiskIopsSupport.SPAN_SLEEP_MS)
            finishSpan()
        }
        sendToHome()
    }

    private fun midSpanBackgroundThenForeground() {
        startLifecycleSpan()
        DiskIopsSupport.generateDiskActivity(context, "lifecycle-mid-start")
        val sawStop = AtomicBoolean(false)
        val ended = AtomicBoolean(false)

        val callbacks =
            object : NoOpActivityLifecycleCallbacks() {
                override fun onActivityStopped(activity: Activity) {
                    if (activity !== context) {
                        return
                    }
                    if (sawStop.compareAndSet(false, true)) {
                        DiskIopsSupport.generateDiskActivity(context, "lifecycle-mid-bg")
                        mainHandler.postDelayed({ bringTaskToForeground() }, BRING_TO_FRONT_DELAY_MS)
                    }
                }

                override fun onActivityResumed(activity: Activity) {
                    if (activity !== context || !sawStop.get() || !ended.compareAndSet(false, true)) {
                        return
                    }
                    application.unregisterActivityLifecycleCallbacks(this)
                    DiskIopsSupport.generateDiskActivity(context, "lifecycle-mid-fg")
                    Thread.sleep(DiskIopsSupport.SPAN_SLEEP_MS)
                    finishSpan()
                }
            }

        application.registerActivityLifecycleCallbacks(callbacks)
        sendToHome()
    }

    private fun runOnceOnStop(action: () -> Unit) {
        val handled = AtomicBoolean(false)
        val callbacks =
            object : NoOpActivityLifecycleCallbacks() {
                override fun onActivityStopped(activity: Activity) {
                    if (activity !== context || !handled.compareAndSet(false, true)) {
                        return
                    }
                    application.unregisterActivityLifecycleCallbacks(this)
                    action()
                }
            }
        application.registerActivityLifecycleCallbacks(callbacks)
    }

    private fun startLifecycleSpan() {
        if (isAppSession) {
            BugsnagPerformance.startAppSessionSpan(APP_SESSION_NAME)
        } else {
            customSpan =
                BugsnagPerformance.startSpan(
                    CUSTOM_SPAN_NAME,
                    DiskIopsSupport.diskSpanOptions(),
                )
        }
    }

    private fun finishSpan() {
        if (isAppSession) {
            BugsnagPerformance.endAppSessionSpan()
        } else {
            customSpan?.end()
            customSpan = null
        }
        mainHandler.post { PerformanceTestUtils.flushBatch() }
    }

    private fun sendToHome() {
        log("DiskIopsLifecycleScenario sending app to HOME")
        mainHandler.post {
            context.startActivity(
                Intent().apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_HOME)
                },
            )
        }
    }

    private fun bringTaskToForeground() {
        log("DiskIopsLifecycleScenario bringing task to foreground")
        val intent =
            Intent(context, context.javaClass).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
        context.startActivity(intent)
    }

    private companion object {
        const val BRING_TO_FRONT_DELAY_MS = 300L
        const val CUSTOM_SPAN_NAME = "DiskIopsCustom"
        const val APP_SESSION_NAME = "DiskIops"
    }
}
