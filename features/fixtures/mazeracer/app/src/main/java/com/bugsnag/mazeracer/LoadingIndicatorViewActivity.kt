package com.bugsnag.mazeracer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity

class LoadingIndicatorViewActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading_indicator_view)
    }

    override fun onStart() {
        super.onStart()
        handler.postDelayed(
            Runnable {
                val v = findViewById<View>(R.id.loading_indicator)
                (v.parent as ViewGroup).removeView(v)
            },
            100L,
        )
    }
}
