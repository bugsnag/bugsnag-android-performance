package com.bugsnag.android.performance.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.takeCurrentBatch
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class MeasuredComposableTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setupTest() {
        // clear any pending spans from the batch before each test runs
        BugsnagPerformance.takeCurrentBatch()
    }

    @Test
    fun measuredScreenLoad() {
        composeTestRule.setContent {
            MeasuredComposable(name = "Parent") {
                LoginScreen()
            }
        }

        with(composeTestRule) {
            onNode(hasText("Email")).assertIsDisplayed()
            onNode(hasText("Password")).assertIsDisplayed()
            onNode(hasText("Invalid username or password")).assertIsDisplayed()
        }

        val spans = BugsnagPerformance.takeCurrentBatch()

        assertEquals(8, spans.size)
        val parentSpan = spans.find { it.name == "[ViewLoad/Compose]Parent" }
        val warningsSpan = spans.find { it.name == "[ViewLoad/Compose]Warnings" }
        val emailSpan = spans.find { it.name == "[ViewLoad/Compose]Email" }
        val passwordSpan = spans.find { it.name == "[ViewLoad/Compose]Password" }
        assertEquals(parentSpan?.spanId, warningsSpan?.parentSpanId)
        assertEquals(parentSpan?.spanId, emailSpan?.parentSpanId)
        assertEquals(parentSpan?.spanId, passwordSpan?.parentSpanId)
        spans.forEach {
            assertEquals(parentSpan?.traceId, it.traceId)
        }
    }
}

@Composable
fun EmailField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        placeholder = { Text("Email") },
        singleLine = true,
    )
}

@Composable
fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        placeholder = { Text("Password") },
        singleLine = true,
    )
}

@Composable
fun LoginScreen() {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp),
    ) {
        MeasuredComposable("Email") {
            EmailField(
                value = "Email",
                onChange = { },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MeasuredComposable("Password") {
            PasswordField(
                value = "Password",
                onChange = { },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    MeasuredComposable("Warnings") {
        Text("Invalid username or password")
    }
}
