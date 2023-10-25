package com.bugsnag.mazeracer

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.bugsnag.android.performance.DoNotEndAppStart

@DoNotEndAppStart
class SplashScreenActivity : AppCompatActivity() {
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
