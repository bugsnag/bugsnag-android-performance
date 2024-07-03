package com.bugsnag.android.performance.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.takeCurrentBatch
import org.junit.Assert
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class ComposeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setupTest() {
        // clear any pending spans from the batch before each test runs
        BugsnagPerformance.takeCurrentBatch()
    }

    @Test
    fun myUIComponentTest() {
        composeTestRule.setContent {
            MeasuredComposable(name = "Login") {
                LogInButton()
            }
        }
        composeTestRule.onNode(hasText("Log In")).performClick()
        composeTestRule.onNode(hasText("Log In")).assertIsDisplayed()

        val spans = BugsnagPerformance.takeCurrentBatch()
        assertEquals(2, spans.size)
    }

}