package com.example.bugsnag.performance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bugsnag.android.performance.BugsnagPerformance

class MainActivity : AppCompatActivity() {
    private val network = ExampleNetworkCalls(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AllButtons(network = network, context = this)

        }
    }
}

@Composable
fun AllButtons(network: ExampleNetworkCalls, context: Context) {
    Column {
        Button(
            onClick = {
                network.runRequest()
            },
            shape = RoundedCornerShape(5.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            Text("Network Request")
        }
        Button(
            onClick = {
                val span = BugsnagPerformance.startSpan("I am custom!")
                Handler(Looper.getMainLooper()).postDelayed(span::end, (10L..2000L).random())
            },
            shape = RoundedCornerShape(5.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            Text("Custom Span")
        }
        Button(
            onClick = {
                context.startActivity(Intent(context, LoadingActivity::class.java))
            },
            shape = RoundedCornerShape(5.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            Text("Loading Activity")
        }

    }
}
