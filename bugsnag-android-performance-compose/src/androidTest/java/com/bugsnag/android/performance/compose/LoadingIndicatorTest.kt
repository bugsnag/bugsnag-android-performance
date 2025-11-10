package com.bugsnag.android.performance.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.ViewType
import com.bugsnag.android.performance.durationMillis
import com.bugsnag.android.performance.takeCurrentBatch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val LOADING_MESSAGE = "Loading..."
private const val VIEW_LOAD_EXTENDED_TIME = 100L

@RunWith(AndroidJUnit4::class)
class LoadingIndicatorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setupTest() {
        // clear any pending spans from the batch before each test runs
        BugsnagPerformance.takeCurrentBatch()
    }

    @Test
    fun extendsViewLoadSpanWithContent() =
        testExtendedViewLoad {
            LoadingIndicatorWrapper {
                LoadingIndicator {
                    Text(LOADING_MESSAGE)
                }
            }
        }

    @Test
    fun extendsViewLoadSpanWithoutContent() =
        testExtendedViewLoad {
            LoadingIndicatorWrapper {
                LoadingIndicator()
                Text(LOADING_MESSAGE)
            }
        }

    @Test
    fun createsChildSpanWithSpanName() =
        testExtendedViewLoad(expectedChildSpanName = "LoadingData") {
            LoadingIndicatorWrapper {
                LoadingIndicator(spanName = "LoadingData") {
                    Text(LOADING_MESSAGE)
                }
            }
        }

    private inline fun testExtendedViewLoad(
        expectedChildSpanName: String? = null,
        crossinline loadingIndicator: @Composable () -> Unit,
    ) = runBlocking {
        var viewLoadSpan: Span? = null

        composeTestRule.setContent {
            viewLoadSpan =
                BugsnagPerformance.startViewLoadSpan(
                    ViewType.COMPOSE,
                    "TestView",
                )

            loadingIndicator()
        }

        composeTestRule.awaitIdle()
        viewLoadSpan!!.end()

        assertTrue("ViewLoad should have ended", viewLoadSpan!!.isEnded())

        with(composeTestRule) {
            // wait for the LoadingIndicator to appear
            waitUntil {
                onAllNodes(hasText(LOADING_MESSAGE)).fetchSemanticsNodes().isNotEmpty()
            }
            // wait for it to finish and disappear again
            waitUntil { onAllNodes(hasText(LOADING_MESSAGE)).fetchSemanticsNodes().isEmpty() }
            awaitIdle()
        }

        assertTrue(
            "LoadingIndicator should have extended the ViewLoad " +
                "duration >=${VIEW_LOAD_EXTENDED_TIME}ms " +
                "(was ${viewLoadSpan!!.durationMillis}ms)",
            viewLoadSpan!!.durationMillis >= VIEW_LOAD_EXTENDED_TIME,
        )

        val batch = BugsnagPerformance.takeCurrentBatch()
        if (expectedChildSpanName != null) {
            val loadingSpan = batch.find { it.name == expectedChildSpanName }

            assertTrue("$expectedChildSpanName span should exist", loadingSpan != null)
            assertTrue(
                "$expectedChildSpanName span should have extended duration >=${VIEW_LOAD_EXTENDED_TIME}ms " +
                    "(was ${loadingSpan!!.durationMillis}ms)",
                loadingSpan.durationMillis >= VIEW_LOAD_EXTENDED_TIME,
            )
            assertEquals(
                "$expectedChildSpanName span should be a child of the ViewLoad span",
                viewLoadSpan!!.spanId,
                loadingSpan.parentSpanId,
            )
        } else {
            // if no spanName was provided, ensure no child spans were created
            assertEquals(
                "No child spans should have been created",
                1,
                batch.count(),
            )
        }
    }
}

@Composable
private fun LoadingIndicatorWrapper(loadingIndicator: @Composable () -> Unit) {
    var loading by remember { mutableStateOf(true) }

    if (loading) {
        loadingIndicator()
    }

    LaunchedEffect(Unit) {
        // we actually wait slightly longer than expected to account for slight differences in the
        // clocks used, and rounding losses in the nano->milli conversion
        delay(VIEW_LOAD_EXTENDED_TIME + (VIEW_LOAD_EXTENDED_TIME / 4))
        loading = false
    }
}
