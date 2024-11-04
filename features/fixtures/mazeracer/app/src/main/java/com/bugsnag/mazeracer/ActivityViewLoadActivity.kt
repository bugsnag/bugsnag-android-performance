package com.bugsnag.mazeracer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.BugsnagPerformance

private const val ON_SCREEN_TIME_MS = 1000L

class ActivityViewLoadActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())

    private val autoInstrument: AutoInstrument
        get() =
            intent.getStringExtra(AUTO_INSTRUMENT_EXTRA)
                ?.let { AutoInstrument.valueOf(it) }
                ?: AutoInstrument.OFF

    override fun onCreate(savedInstanceState: Bundle?) {
        if (autoInstrument == AutoInstrument.OFF) {
            BugsnagPerformance.startViewLoadSpan(this)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_load)

        supportFragmentManager
            .beginTransaction()
            .add(R.id.fragment_container, LoaderFragment())
            .commit()
    }

    override fun onResume() {
        super.onResume()

        handler.postDelayed(
            {
                if (intent.getBooleanExtra(END_VIEW_LOAD_SPAN, false)) {
                    BugsnagPerformance.endViewLoadSpan(this)
                }

                finish()
            },
            ON_SCREEN_TIME_MS,
        )
    }

    class LoaderFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? {
            return inflater.inflate(R.layout.fragment_view_load, container, false)
        }
    }

    companion object {
        const val AUTO_INSTRUMENT_EXTRA = "com.bugsnag.android.performance.extra.autoInstrument"
        const val END_VIEW_LOAD_SPAN = "com.bugsnag.android.performance.extra.endViewLoadSpan"

        fun intent(
            context: Context,
            autoInstrument: AutoInstrument,
            endViewLoadSpan: Boolean = autoInstrument != AutoInstrument.FULL,
        ): Intent {
            return Intent(context, ActivityViewLoadActivity::class.java)
                .putExtra(AUTO_INSTRUMENT_EXTRA, autoInstrument.name)
                .putExtra(END_VIEW_LOAD_SPAN, endViewLoadSpan)
        }
    }
}
