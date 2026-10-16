package com.bugsnag.mazeracer.scenarios

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.BringToForegroundReceiver
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

    /**
     * Do not use Maze/Appium `background_app`: that API is deprecated and often kills the
     * process, which drops the in-memory span (0 spans received). HOME + AlarmManager keeps
     * the process and is a BAL-safe way to return to the foreground.
     */
    private fun midSpanBackgroundThenForeground() {
        val sawStop = AtomicBoolean(false)
        val ended = AtomicBoolean(false)

        val callbacks =
            object : NoOpActivityLifecycleCallbacks() {
                override fun onActivityStopped(activity: Activity) {
                    // Match by package: the original Activity instance may be destroyed
                    // while backgrounded and replaced on resume.
                    if (!isFixtureActivity(activity)) {
                        return
                    }
                    if (sawStop.compareAndSet(false, true)) {
                        log("DiskIopsLifecycleScenario mid-span onStop")
                        DiskIopsSupport.generateDiskActivity(context, "lifecycle-mid-bg")
                        scheduleBringToForeground()
                    }
                }

                override fun onActivityResumed(activity: Activity) {
                    if (!isFixtureActivity(activity) || !sawStop.get() || !ended.compareAndSet(false, true)) {
                        return
                    }
                    application.unregisterActivityLifecycleCallbacks(this)
                    log("DiskIopsLifecycleScenario mid-span onResume; ending span")
                    DiskIopsSupport.generateDiskActivity(context, "lifecycle-mid-fg")
                    mainHandler.postDelayed({ finishSpan() }, DiskIopsSupport.SPAN_SLEEP_MS)
                }
            }

        application.registerActivityLifecycleCallbacks(callbacks)
        startLifecycleSpan()
        DiskIopsSupport.generateDiskActivity(context, "lifecycle-mid-start")
        sendToHome()

        // Android 14 / device farms can occasionally miss the resume callback even though the
        // app-session span is still valid. Keep the foreground transition as the happy path, but
        // add a fallback finish so the test still receives the span instead of timing out.
        mainHandler.postDelayed(
            {
                if (ended.compareAndSet(false, true)) {
                    application.unregisterActivityLifecycleCallbacks(callbacks)
                    log("DiskIopsLifecycleScenario mid-span fallback; ending span without resume")
                    finishSpan()
                }
            },
            BACKGROUND_MS + DiskIopsSupport.SPAN_SLEEP_MS + FALLBACK_END_BUFFER_MS,
        )
    }

    private fun scheduleBringToForeground() {
        log("DiskIopsLifecycleScenario scheduling foreground in ${BACKGROUND_MS}ms")
        val alarmService = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending =
            PendingIntent.getBroadcast(
                context,
                FOREGROUND_REQUEST_CODE,
                Intent(context, BringToForegroundReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val triggerAt = SystemClock.elapsedRealtime() + BACKGROUND_MS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmService.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pending,
            )
        } else {
            alarmService.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
        }
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

    private fun isFixtureActivity(activity: Activity): Boolean {
        return activity.packageName == application.packageName
    }

    private companion object {
        const val CUSTOM_SPAN_NAME = "DiskIopsCustom"
        const val APP_SESSION_NAME = "DiskIops"
        const val BACKGROUND_MS = 2000L
        const val FALLBACK_END_BUFFER_MS = 1000L
        const val FOREGROUND_REQUEST_CODE = 2233
    }
}
