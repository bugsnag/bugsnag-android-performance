package com.example.bugsnag.performance

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.compose.MeasuredComposable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val network = ExampleNetworkCalls(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true
        insetsController.isAppearanceLightNavigationBars = true
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent {
            MeasuredComposable(name = "MainActivity") {
                Column(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
                ) {
                    AllButtons(network = network)
                }
            }
        }
    }
}

private var frameIndex = 0

@Composable
fun AllButtons(network: ExampleNetworkCalls) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var renderingSlowFrames by remember { mutableStateOf(false) }

    Column {
        DemoAction(label = "Network Request") {
            network.runRequest()
        }

        DemoAction(label = "Custom Span") {
            val span = BugsnagPerformance.startSpan(
                "I am custom!",
                SpanOptions.makeCurrentContext(true),
            )

            coroutineScope.launch {
                delay(5000L)
                span.end()
            }
        }

        DemoAction(label = "Animation with Slow/Frozen Frames") {
            frameIndex = 0
            renderingSlowFrames = true

            val span = BugsnagPerformance.startSpan(
                "Slow Frames Container",
                SpanOptions.makeCurrentContext(true),
            )

            coroutineScope.launch {
                delay(5000L)
                span.end()
                renderingSlowFrames = false
            }
        }

        if (renderingSlowFrames) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        frameIndex++

                        if (frameIndex % 5 == 0) {
                            // delay enough to drop a frame or two
                            Thread.sleep(30L)
                        }

                        if (frameIndex % 100 == 0) {
                            // simulate a frozen frame
                            Thread.sleep(800L)
                        }

                        drawContent()
                    },
            )
        }

        DemoAction(label = "Open Secondary Activity") {
            context.startActivity(Intent(context, LoadingActivity::class.java))
        }
    }
}
