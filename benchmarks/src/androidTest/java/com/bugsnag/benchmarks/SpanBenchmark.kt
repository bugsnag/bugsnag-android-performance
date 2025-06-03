package com.bugsnag.benchmarks

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.ViewType
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URL

@RunWith(AndroidJUnit4::class)
class SpanBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    companion object {
        @JvmStatic
        @BeforeClass
        fun configure() {
            BugsnagPerformance.start(
                PerformanceConfiguration(
                    ApplicationProvider.getApplicationContext(),
                    "not-valid-api-key",
                ).apply {
                    // we set the endpoint to localhost so that no payloads can actually be delivered
                    endpoint = "http://localhost"
                    // discard spans on end()
                    releaseStage = "benchmarking"
                    enabledReleaseStages = setOf("production")
                    logger = NoopLogger
                },
            )
        }
    }

    @Test
    fun logCustomSpans() {
        benchmarkRule.measureRepeated {
            BugsnagPerformance.startSpan("Test span").end()
        }
    }

    @Test
    fun logViewLoadSpans() {
        val activity = MyActivity()
        benchmarkRule.measureRepeated {
            BugsnagPerformance
                .startViewLoadSpan(ViewType.ACTIVITY, activity::class.java.simpleName)
                .end()
        }
    }

    @Test
    fun logNetworkRequestSpans() {
        val url = URL("http://localhost")
        benchmarkRule.measureRepeated {
            BugsnagPerformance.startNetworkRequestSpan(url, "POST")?.end()
        }
    }

    @Test
    fun nestedViewSpans() {
        benchmarkRule.measureRepeated {
            BugsnagPerformance.startViewLoadSpan(ViewType.ACTIVITY, "MainActivity").use {
                BugsnagPerformance.startViewLoadSpan(ViewType.FRAGMENT, "InboxFragment").use {
                    BugsnagPerformance.startSpan("LoadInbox").end()
                }

                BugsnagPerformance.startViewLoadSpan(ViewType.FRAGMENT, "ReadMessageFragment").end()
            }
        }
    }

    // it's not really an Activity, but we pretend it is
    class MyActivity
}

internal object NoopLogger : Logger {
    override fun e(msg: String): Unit = Unit

    override fun e(
        msg: String,
        throwable: Throwable,
    ): Unit = Unit

    override fun w(msg: String): Unit = Unit

    override fun w(
        msg: String,
        throwable: Throwable,
    ): Unit = Unit

    override fun i(msg: String): Unit = Unit

    override fun i(
        msg: String,
        throwable: Throwable,
    ): Unit = Unit

    override fun d(msg: String): Unit = Unit

    override fun d(
        msg: String,
        throwable: Throwable,
    ): Unit = Unit
}
