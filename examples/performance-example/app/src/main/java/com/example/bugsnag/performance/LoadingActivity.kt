package com.example.bugsnag.performance

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.bugsnag.android.performance.BugsnagPerformance

class LoadingActivity : AppCompatActivity() {

    private lateinit var close: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setContent {
//             loading()
//        }

        setContentView(R.layout.activity_loading)

        close = findViewById(R.id.close)
        close.setOnClickListener {
            finish()
        }

        progressBar = findViewById(R.id.progress_bar)

        Thread.sleep(200L)
    }

    override fun onStart() {
        super.onStart()
        Thread.sleep(150L)
    }

    override fun onResume() {
        val secondaryLoadSpan = BugsnagPerformance.startSpan("SecondaryLoad")

        Handler(Looper.getMainLooper()).postDelayed(
            {
                progressBar.visibility = View.GONE
                close.visibility = View.VISIBLE

                secondaryLoadSpan.end()
            },
            500L,
        )

        super.onResume()
    }
}

//@OptIn(ExperimentalMaterial3Api::class)
//@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
//@Preview
//@Composable
//fun loading() {
//    val context = LocalContext.current as Activity
//
//    LaunchedEffect(Unit) {
//        delay(200L)
//        context.finish()
//    }
//
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text("Loading...") },
//                navigationIcon = {
//                    IconButton(onClick = {
//                        context.finish()
//                    }) {
//                        Icon(Icons.Filled.Close, contentDescription = "Close")
//                    }
//                }
//            )
//        },
//
//        ) {
//        Box(
//            modifier = Modifier.fillMaxSize(),
//            contentAlignment = Alignment.Center
//        ) {
//            CircularProgressIndicator()
//        }
//    }
//
//}