package com.bugsnag.android.performance.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.bugsnag.android.performance.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class Api24NetworkTypeTest {
    @Test
    fun wired() {
        testTransportType(NetworkCapabilities.TRANSPORT_ETHERNET, NetworkType.WIRED)
        testTransportType(NetworkCapabilities.TRANSPORT_USB, NetworkType.WIRED)
    }

    @Test
    fun wifi() {
        testTransportType(NetworkCapabilities.TRANSPORT_WIFI, NetworkType.WIFI)
    }

    @Test
    fun cell() {
        testTransportType(NetworkCapabilities.TRANSPORT_CELLULAR, NetworkType.CELL)
    }

    private fun testTransportType(transportType: Int, expectedNetworkType: NetworkType) {
        val context = mock<Context>()
        val connectivityManager = mock<ConnectivityManager>()
        var callbackInvoked = false

        val connectivity = ConnectivityApi24(context, connectivityManager) { _, _, networkType, _ ->
            assertEquals(expectedNetworkType, networkType)
            callbackInvoked = true
        }

        val networkCapabilities = mock<NetworkCapabilities>()
        whenever(networkCapabilities.hasTransport(eq(transportType)))
            .thenReturn(true)
        whenever(networkCapabilities.hasCapability(eq(NetworkCapabilities.NET_CAPABILITY_INTERNET)))
            .thenReturn(true)

        whenever(connectivityManager.getNetworkCapabilities(any()))
            .thenReturn(networkCapabilities)

        connectivity.onAvailable(mock()) // first event is always ignored
        connectivity.onAvailable(mock())

        assertTrue("Network callback not invoked", callbackInvoked)
    }
}
