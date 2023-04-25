package com.example.bugsnag.performance

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper

class LoadingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        Thread.sleep(200L)
    }

    override fun onStart() {
        super.onStart()
        Thread.sleep(150L)
    }

    override fun onResume() {
        super.onResume()
        Handler(Looper.getMainLooper()).post {
            finish()
        }
    }
}