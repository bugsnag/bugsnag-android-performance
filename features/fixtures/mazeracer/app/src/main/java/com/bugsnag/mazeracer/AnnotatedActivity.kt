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
import com.bugsnag.android.performance.BugsnagName

private const val ON_SCREEN_TIME_MS = 1000L

@BugsnagName("TestActivityName")
class AnnotatedActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_load)

        supportFragmentManager
            .beginTransaction()
            .add(R.id.fragment_container, AnnotatedFragment())
            .commit()
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(
            {
                finish()
            },
            ON_SCREEN_TIME_MS,
        )
    }

    @BugsnagName("TestFragmentName")
    class AnnotatedFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? {
            return inflater.inflate(R.layout.fragment_view_load, container, false)
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AnnotatedActivity::class.java)
        }
    }
}
