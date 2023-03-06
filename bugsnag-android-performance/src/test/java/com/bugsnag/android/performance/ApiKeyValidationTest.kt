package com.bugsnag.android.performance

import android.content.Context
import com.bugsnag.android.performance.internal.ImmutableConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ApiKeyValidationTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun invalidApiKey() {
        assertThrows(IllegalArgumentException::class.java) {
            ImmutableConfig(PerformanceConfiguration.load(context, "not a valid key"))
        }
    }

    @Test
    fun uppercaseApiKeyInvalid() {
        assertThrows(IllegalArgumentException::class.java) {
            ImmutableConfig(
                PerformanceConfiguration.load(
                    context,
                    "DECAFBADDECAFBADDECAFBADDECAFBAD",
                ),
            )
        }
    }

    @Test
    fun validApiKey() {
        val apiKey = "decafbaddecafbaddecafbaddecafbad"
        val config = ImmutableConfig(PerformanceConfiguration.load(context, apiKey))

        assertEquals(apiKey, config.apiKey)
    }

    @Test
    fun unvalidatedConfig() {
        // test that constructing a PerformanceConfiguration does not throw an error
        PerformanceConfiguration.load(context, "invalid api key")
    }
}
