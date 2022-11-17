package com.bugsnag.android.performance.internal

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any

internal class ForegroundTrackerTest {
    @Test
    fun testInForeground() = withStaticMock<ActivityManager> { activityManager ->
        activityManager.`when`<Unit> { ActivityManager.getMyMemoryState(any()) }
            .then { answer ->
                val runningInfo = answer.arguments[0] as ActivityManager.RunningAppProcessInfo
                runningInfo.importance = IMPORTANCE_FOREGROUND

                Unit
            }

        assertEquals(true, ForegroundTracker.isInForeground)
    }

    @Test
    fun testInBackground() = withStaticMock<ActivityManager> { activityManager ->
        activityManager.`when`<Unit> { ActivityManager.getMyMemoryState(any()) }
            .then { answer ->
                val runningInfo = answer.arguments[0] as ActivityManager.RunningAppProcessInfo
                runningInfo.importance = IMPORTANCE_CACHED

                Unit
            }

        assertEquals(false, ForegroundTracker.isInForeground)
    }

    @Test
    fun testFailure() = withStaticMock<ActivityManager> { activityManager ->
        activityManager.`when`<Unit> { ActivityManager.getMyMemoryState(any()) }
            .thenThrow(RuntimeException())

        assertNull(ForegroundTracker.isInForeground)
    }
}
