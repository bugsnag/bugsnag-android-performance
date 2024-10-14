package com.bugsnag.mazeracer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Text
import com.bugsnag.android.performance.compose.MeasuredComposable

private const val ON_SCREEN_TIME_MS = 1000L

class ComposeViewLoadActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeasuredComposable("Composable") {
                Text("This is a composable view")
            }
        }
    }

    override fun onResume() {
        super.onResume()

        handler.postDelayed(
            { finish() },
            ON_SCREEN_TIME_MS,
        )
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, ActivityViewLoadActivity::class.java)
        }
    }
}
