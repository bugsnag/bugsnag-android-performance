package com.bugsnag.mazeracer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.DoNotEndAppStart
import com.bugsnag.android.performance.controls.SpanType

@DoNotEndAppStart
class SplashScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BugsnagPerformance.getSpanControls(SpanType.AppStart)?.setType("SplashScreen")
    }

    override fun onResume() {
        super.onResume()
        Handler(Looper.getMainLooper()).postDelayed(
            {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            },
            250L,
        )
    }
}
