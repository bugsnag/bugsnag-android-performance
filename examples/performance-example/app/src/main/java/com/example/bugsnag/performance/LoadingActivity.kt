package com.example.bugsnag.performance

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bugsnag.android.performance.BugsnagPerformance
import kotlinx.coroutines.delay

class LoadingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LoadingScreen()
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
    val visibility = remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val secondaryLoadSpan = BugsnagPerformance.startSpan("SecondaryLoad")
        delay(500L)
        visibility.value = false
        secondaryLoadSpan.end()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (visibility.value) {
            CircularProgressIndicator()
        } else {
            Button(onClick = { context.finish() }) {
                Text(text = "Close")

            }
        }
    }
}
