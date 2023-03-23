package com.bugsnag.mazeracer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.coroutines.BugsnagPerformanceScope
import com.bugsnag.android.performance.measureSpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NestedSpansActivity : AppCompatActivity(), CoroutineScope by BugsnagPerformanceScope() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_load)

        // load first fragment
        supportFragmentManager
            .beginTransaction()
            .add(R.id.fragment_container, FirstFragment())
            .commit()
    }

    override fun onResume() {
        super.onResume()
        // start a custom span
        val customRootSpan = BugsnagPerformance.startSpan("CustomRoot")

        // load a second fragment
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, SecondFragment())
            .commit()

        // launch a coroutine
        launch {
            measureSpan("DoStuff") {
                "Do stuff"
            }

            // move to an IO thread
            withContext(Dispatchers.IO) {
                measureSpan("LoadData") {
                    "Load some data"
                }
            }

            // end the custom span
            customRootSpan.end()
        }

        finish()
    }

    class FirstFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? {
            return inflater.inflate(R.layout.fragment_1, container, false)
        }
    }

    class SecondFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? {
            return inflater.inflate(R.layout.fragment_2, container, false)
        }
    }
}
