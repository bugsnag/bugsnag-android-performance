package com.bugsnag.android.performance.compose

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bugsnag.android.performance.BugsnagPerformance
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

    @Test
    fun myUIComponentTest() {
//        val mockBugsnag = mock<BugsnagPerformance>()
        try {
//            mockBugsnag.`when`<Void> { BugsnagPerformance.startViewLoadSpan(any()) }
//                .thenAnswer { null }
//
//            mockBugsnag.verify({ BugsnagPerformance.startViewLoadSpan(any()) }, never())
            composeTestRule.setContent {
                LogInButton()
            }
            composeTestRule.onNode(hasText("Log In")).performClick()
        } finally {
//            mockBugsnag.close()
        }
    }

}