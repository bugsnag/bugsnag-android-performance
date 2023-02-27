package com.bugsnag.mazeracer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.internal.SpanImpl

private const val ON_SCREEN_TIME_MS = 150L

class ActivityViewLoadActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    private val autoInstrument: AutoInstrument
        get() = intent.getStringExtra(AUTO_INSTRUMENT_EXTRA)
            ?.let { AutoInstrument.valueOf(it) }
            ?: AutoInstrument.OFF

    override fun onCreate(savedInstanceState: Bundle?) {
        if (autoInstrument == AutoInstrument.OFF) {
            BugsnagPerformance.startViewLoadSpan(this).also {
                (it as SpanImpl).attributes["manual_start"] = true
            }
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_load)
    }

    override fun onResume() {
        super.onResume()

        handler.postDelayed(
            {
                if (autoInstrument != AutoInstrument.FULL) {
                    BugsnagPerformance.endViewLoadSpan(this)
                }

                finish()
            },
            ON_SCREEN_TIME_MS
        )
    }

    companion object {
        const val AUTO_INSTRUMENT_EXTRA = "com.bugsnag.android.performance.extra.autoInstrument"

        fun intent(context: Context, autoInstrument: AutoInstrument): Intent {
            return Intent(context, ActivityViewLoadActivity::class.java)
                .putExtra(AUTO_INSTRUMENT_EXTRA, autoInstrument.name)
        }
    }
}
