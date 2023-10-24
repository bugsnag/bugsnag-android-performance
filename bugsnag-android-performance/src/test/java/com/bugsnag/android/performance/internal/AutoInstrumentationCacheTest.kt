package com.bugsnag.android.performance.internal

import android.app.Activity
import com.bugsnag.android.performance.AutoInstrumentationCache
import com.bugsnag.android.performance.DoNotEndAppStart
import com.bugsnag.android.performance.DoNotInstrument
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AutoInstrumentationCacheTest {

    @DoNotInstrument
    class DoNotInstrumentActivity : Activity()

    @DoNotEndAppStart
    class DoNotEndAppStartActivity : Activity()

    @DoNotInstrument
    @DoNotEndAppStart
    class NoInstrumentationAppStartActivity : Activity()

    class NoAnnotatedActivity : Activity()

    @Test
    fun testIsInstrumentationEnabled() {
        val result =
            AutoInstrumentationCache().isInstrumentationEnabled(DoNotInstrumentActivity::class.java)
        assertFalse(result)
    }

    @Test
    fun testIsAppStartActivity() {
        val result =
            AutoInstrumentationCache().isAppStartActivity(DoNotEndAppStartActivity::class.java)
        assertTrue(result)
    }

    @Test
    fun testNoAnnotatedActivity() {
        val result1 =
            AutoInstrumentationCache().isInstrumentationEnabled(NoAnnotatedActivity::class.java)
        assertTrue(result1)
        val result2 =
            AutoInstrumentationCache().isAppStartActivity(NoAnnotatedActivity::class.java)
        assertFalse(result2)
    }

    @Test
    fun testInstrumentationNotEnabled() {
        val result =
            AutoInstrumentationCache().isInstrumentationEnabled(NoInstrumentationAppStartActivity::class.java)
        assertFalse(result)
    }

    @Test
    fun testAppStartActivityNotEnabled() {
        val result =
            AutoInstrumentationCache().isAppStartActivity(NoInstrumentationAppStartActivity::class.java)
        assertTrue(result)
    }

    @Test
    fun testDoNotInstrumentActivities() {
        val doNotInstrumentActivities = hashSetOf<Class<Any>>()
        doNotInstrumentActivities.add(DoNotInstrumentActivity::class.java as Class<Any>)
        doNotInstrumentActivities.add(DoNotEndAppStartActivity::class.java as Class<Any>)

        val autoInstrumentationCache = AutoInstrumentationCache().apply {
            configure(hashSetOf(), doNotInstrumentActivities)
        }

        val isInstrumentationResult1 =
            autoInstrumentationCache.isInstrumentationEnabled(doNotInstrumentActivities.last())
        assertFalse(isInstrumentationResult1)

        val isInstrumentationResult2 =
            autoInstrumentationCache.isInstrumentationEnabled(doNotInstrumentActivities.first())
        assertFalse(isInstrumentationResult2)
    }

    @Test
    fun testDoNotEndAppStartActivities() {
        val doNotEndAppStartActivities = hashSetOf<Class<out Activity>>()
        doNotEndAppStartActivities.add(DoNotEndAppStartActivity::class.java as Class<out Activity>)
        val autoInstrumentationCache = AutoInstrumentationCache().apply {
            configure(doNotEndAppStartActivities, hashSetOf())
        }

        val isEndAppStartResult1 =
            autoInstrumentationCache.isAppStartActivity(doNotEndAppStartActivities.first())
        assertTrue(isEndAppStartResult1)

        val isEndAppStartResult2 =
            autoInstrumentationCache.isInstrumentationEnabled(doNotEndAppStartActivities.first())
        assertTrue(isEndAppStartResult2)
    }

    @Test
    fun testMixedActivities() {
        val doNotInstrumentActivities = hashSetOf(
            DoNotEndAppStartActivity::class.java as Class<Any>,
            NoAnnotatedActivity::class.java as Class<Any>,
        )
        val doNotEndAppStartActivities = hashSetOf(
            DoNotInstrumentActivity::class.java,
            NoAnnotatedActivity::class.java,
        )

        val autoInstrumentationCache = AutoInstrumentationCache().apply {
            configure(doNotEndAppStartActivities, doNotInstrumentActivities)
        }

        val isInstrumentationResult1 =
            autoInstrumentationCache.isInstrumentationEnabled(doNotInstrumentActivities.last())
        assertFalse(isInstrumentationResult1)

        val isInstrumentationResult2 =
            autoInstrumentationCache.isInstrumentationEnabled(doNotInstrumentActivities.first())
        assertFalse(isInstrumentationResult2)

        val isEndAppStartResult1 =
            autoInstrumentationCache.isAppStartActivity(doNotEndAppStartActivities.last())
        assertTrue(isEndAppStartResult1)

        val isEndAppStartResult2 =
            autoInstrumentationCache.isAppStartActivity(doNotEndAppStartActivities.first())
        assertTrue(isEndAppStartResult2)
    }
}
