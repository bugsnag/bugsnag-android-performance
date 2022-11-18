package com.bugsnag.android.performance.internal

import android.content.Context
import android.net.ConnectivityManager
import com.bugsnag.android.performance.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@Suppress("DEPRECATION")
class LegacyNetworkTypeTest {
    @Test
    fun legacyNetworkType() {
        val context = mock<Context>()
        val connectivityManager = mock<ConnectivityManager>()

        whenever(connectivityManager.activeNetworkInfo)
            .thenReturn(newNetworkInfo(ConnectivityManager.TYPE_WIFI))
            .thenReturn(newNetworkInfo(ConnectivityManager.TYPE_MOBILE))
            .thenReturn(newNetworkInfo(ConnectivityManager.TYPE_ETHERNET))
            .thenReturn(newNetworkInfo(ConnectivityManager.TYPE_BLUETOOTH))
            .thenReturn(null)

        val connectivity = ConnectivityLegacy(context, connectivityManager) { _, _ -> }

        assertEquals(NetworkType.WIFI, connectivity.networkType)
        assertEquals(NetworkType.CELL, connectivity.networkType)
        assertEquals(NetworkType.WIRED, connectivity.networkType)
        assertEquals(NetworkType.UNKNOWN, connectivity.networkType)
        assertEquals(NetworkType.UNAVAILABLE, connectivity.networkType)
    }

    private fun newNetworkInfo(type: Int): android.net.NetworkInfo =
        object : android.net.NetworkInfo(type, 0, null, null) {
            override fun getType(): Int = type
        }
}
