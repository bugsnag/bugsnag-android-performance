package com.bugsnag.android.performance.internal.instrumentation

import android.app.Activity
import com.bugsnag.android.performance.internal.Loopers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPausedSystemClock
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowPausedSystemClock::class])
class ForegroundStateTest {

    private val activities = Array(3) { Activity() }

    @Before
    fun resetInBackground() {
        ForegroundState.isInForeground = false
    }

    @Test
    fun applicationCycle() {
        assertFalse(ForegroundState.isInForeground)
        // create 1 activity - assert that the app is still in background (Activity is not started)
        ForegroundState.onActivityCreated(activities[0], null)
        assertFalse(ForegroundState.isInForeground)
        ForegroundState.onActivityStarted(activities[0])
        assertTrue(ForegroundState.isInForeground)
        ForegroundState.onActivityResumed(activities[0])
        assertTrue(ForegroundState.isInForeground)

        for (i in 1..activities.lastIndex) {
            ForegroundState.onActivityCreated(activities[i], null)
            ForegroundState.onActivityStarted(activities[i])
            ForegroundState.onActivityResumed(activities[i])
            assertTrue(ForegroundState.isInForeground)
        }

        // shutdown the Activites one-by-one
        for (i in activities.lastIndex downTo 0) {
            ForegroundState.onActivityPaused(activities[i])
            ForegroundState.onActivityStopped(activities[i])
            ForegroundState.onActivityDestroyed(activities[i])
            assertTrue(ForegroundState.isInForeground)
        }

        // we should still be in the foreground at this point in-case of Activity restarts
        // now we flush the main message queue and the status should change
        Shadows.shadowOf(Loopers.main).runToEndOfTasks()
        assertFalse(ForegroundState.isInForeground)
    }

    @Test
    fun events() {
        val listener = mock<InForegroundCallback>()
        ForegroundState.addForegroundChangedCallback(listener)
        try {
            val activity = activities.first()
            startActivity(activity)
            destroyActivity(activity)
            ShadowPausedSystemClock.advanceBy(200, TimeUnit.MILLISECONDS)
            // immediately restart
            startActivity(activity)
            destroyActivity(activity)

            Shadows.shadowOf(Loopers.main).runToEndOfTasks()

            verify(listener, times(1)).invoke(eq(true))
            verify(listener, times(1)).invoke(eq(false))
        } finally {
            ForegroundState.removeForegroundChangedCallback(listener)
        }
    }

    private fun destroyActivity(activity: Activity) {
        ForegroundState.onActivityPaused(activity)
        ForegroundState.onActivityStopped(activity)
        ForegroundState.onActivityDestroyed(activity)
    }

    private fun startActivity(activity: Activity) {
        ForegroundState.onActivityCreated(activity, null)
        ForegroundState.onActivityStarted(activity)
        ForegroundState.onActivityResumed(activity)
    }
}
