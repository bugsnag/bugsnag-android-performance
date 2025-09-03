package com.example.bugsnag.performance

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bugsnag.android.performance.compose.MeasuredComposable
import com.bugsnag.android.performance.measureSpan
import kotlinx.coroutines.delay

class LoadingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MeasuredComposable(name = "LoadingActivity") {
                LoadingScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Thread.sleep(150L)
    }
}

@Composable
fun LoadingScreen() {
    val context = LocalContext.current as Activity
    var visibility by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        measureSpan("SecondaryLoad") {
            // simulate doing some work
            delay(500L)
        }
        visibility = false
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (visibility) {
            CircularProgressIndicator()
        } else {
            Button(onClick = { context.finish() }) {
                Text(text = "Close")

            }
        }
    }
}
