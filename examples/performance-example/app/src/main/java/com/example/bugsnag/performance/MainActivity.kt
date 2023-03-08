package com.example.bugsnag.performance

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.bugsnag.android.performance.BugsnagPerformance
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private val network = ExampleNetworkCalls(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.network_request).setOnClickListener {
            network.runRequest()
        }

        findViewById<Button>(R.id.custom_span).setOnClickListener {
            val span = BugsnagPerformance.startSpan("I am custom!")
            Handler(Looper.getMainLooper()).postDelayed(span::end, (10L..2000L).random())
        }
    }
}
