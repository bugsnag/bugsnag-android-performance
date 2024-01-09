package com.bugsnag.android.performance.internal

import android.app.Activity
import com.bugsnag.android.performance.DoNotAutoInstrument
import com.bugsnag.android.performance.DoNotEndAppStart
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AutoInstrumentationCacheTest {

    @DoNotAutoInstrument
    class DoNotAutoInstrumentActivity : Activity()

    @DoNotEndAppStart
    class DoNotEndAppStartActivity : Activity()

    @DoNotAutoInstrument
    @DoNotEndAppStart
    class NoInstrumentationAppStartActivity : Activity()

    class NoAnnotatedActivity : Activity()

    @Test
    fun testIsInstrumentationEnabled() {
        val result =
            AutoInstrumentationCache().isInstrumentationEnabled(DoNotAutoInstrumentActivity::class.java)
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
    fun testDoNotAutoInstrumentActivities() {
        val doNotAutoInstrumentActivities = hashSetOf<Class<out Any>>()
        doNotAutoInstrumentActivities.add(DoNotAutoInstrumentActivity::class.java)
        doNotAutoInstrumentActivities.add(DoNotEndAppStartActivity::class.java)

        val autoInstrumentationCache = AutoInstrumentationCache().apply {
            configure(hashSetOf(), doNotAutoInstrumentActivities)
        }

        val isInstrumentationResult1 =
            autoInstrumentationCache.isInstrumentationEnabled(doNotAutoInstrumentActivities.last())
        assertFalse(isInstrumentationResult1)

        val isInstrumentationResult2 =
            autoInstrumentationCache.isInstrumentationEnabled(doNotAutoInstrumentActivities.first())
        assertFalse(isInstrumentationResult2)
    }

    @Test
    fun testDoNotEndAppStartActivities() {
        val doNotEndAppStartActivities = hashSetOf<Class<out Activity>>()
        doNotEndAppStartActivities.add(DoNotEndAppStartActivity::class.java)
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
        val doNotAutoInstrumentFragmentActivities = hashSetOf(
            DoNotEndAppStartActivity::class.java,
            NoAnnotatedActivity::class.java,
        )
        val doNotEndAppStartActivities = hashSetOf(
            DoNotAutoInstrumentActivity::class.java,
            NoAnnotatedActivity::class.java,
        )

        val autoInstrumentationCache = AutoInstrumentationCache().apply {
            configure(doNotEndAppStartActivities, doNotAutoInstrumentFragmentActivities)
        }

        val isInstrumentationResult1 =
            autoInstrumentationCache.isInstrumentationEnabled(doNotAutoInstrumentFragmentActivities.last())
        assertFalse(isInstrumentationResult1)

        val isInstrumentationResult2 =
            autoInstrumentationCache.isInstrumentationEnabled(doNotAutoInstrumentFragmentActivities.first())
        assertFalse(isInstrumentationResult2)

        val isEndAppStartResult1 =
            autoInstrumentationCache.isAppStartActivity(doNotEndAppStartActivities.last())
        assertTrue(isEndAppStartResult1)

        val isEndAppStartResult2 =
            autoInstrumentationCache.isAppStartActivity(doNotEndAppStartActivities.first())
        assertTrue(isEndAppStartResult2)
    }
}
