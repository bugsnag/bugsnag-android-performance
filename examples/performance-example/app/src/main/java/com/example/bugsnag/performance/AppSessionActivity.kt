package com.example.bugsnag.performance

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.compose.MeasuredComposable

class AppSessionActivity : AppCompatActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MeasuredComposable(name = "AppSessionActivity") {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = colorResource(R.color.purple_500),
                                titleContentColor = colorResource(R.color.white),
                            ),
                            title = {
                                Text(text = "App Session Testing")
                            },
                        )
                    },
                ) { contentPadding ->
                    AppSessionMediaContent(contentPadding)
                }
            }
        }
    }
}

@Composable
fun AppSessionMediaContent(contentPadding: PaddingValues) {
    val scrollStateVertical = rememberScrollState()
    
    Column(
        modifier = Modifier
            .padding(contentPadding)
            .verticalScroll(scrollStateVertical),
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // --- Other Sections below ---
        Text("General", modifier = Modifier.padding(8.dp))
        DemoAction(label = "Start Default Session") {
            BugsnagPerformance.startAppSessionSpan()
        }
        DemoAction(label = "End Current Session") {
            BugsnagPerformance.endAppSessionSpan()
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Manual Background Session Testing", modifier = Modifier.padding(8.dp))
        Text(
            "1. Start a session using any of the buttons.\n" +
            "2. Background the app.\n" +
            "3. The session remains active indefinitely.\n" +
            "4. Return and click 'End' to finish.\n" +
            "Note: Background segments report CPU/Memory if a workload is active.",
            modifier = Modifier.padding(8.dp)
        )

        Spacer(modifier = Modifier.height(100.dp))
    }
}
