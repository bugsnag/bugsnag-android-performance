package com.bugsnag.android.performance.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.bugsnag.android.performance.internal.connectivity.ConnectivityApi24
import com.bugsnag.android.performance.internal.connectivity.NetworkType
import com.bugsnag.android.performance.internal.connectivity.shouldAttemptDelivery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
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

    @Test
    fun shouldAttemptDeliveryRefreshesStaleStatus() {
        val context = mock<Context>()
        val connectivityManager = mock<ConnectivityManager>()
        val network = mock<android.net.Network>()
        val networkCapabilities = mock<NetworkCapabilities>()

        whenever(connectivityManager.activeNetwork).thenReturn(network, null, network)
        whenever(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            .thenReturn(true)
        whenever(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            .thenReturn(true)
        whenever(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            .thenReturn(true)
        whenever(connectivityManager.getNetworkCapabilities(network))
            .thenReturn(networkCapabilities)

        val connectivity = ConnectivityApi24(context, connectivityManager, null)

        connectivity.onLost(network)

        assertFalse(connectivity.connectivityStatus.hasConnection)
        assertTrue(connectivity.shouldAttemptDelivery())
    }

    @Test
    fun shouldAttemptDeliveryWhenNetworkIsInternetCapableButUnvalidated() {
        val context = mock<Context>()
        val connectivityManager = mock<ConnectivityManager>()
        val network = mock<android.net.Network>()
        val networkCapabilities = mock<NetworkCapabilities>()

        whenever(connectivityManager.activeNetwork).thenReturn(network)
        whenever(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            .thenReturn(true)
        whenever(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            .thenReturn(true)
        whenever(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            .thenReturn(false)
        whenever(connectivityManager.getNetworkCapabilities(network))
            .thenReturn(networkCapabilities)

        val connectivity = ConnectivityApi24(context, connectivityManager, null)

        assertTrue(connectivity.shouldAttemptDelivery())
        assertTrue(connectivity.connectivityStatus.hasConnection)
        assertEquals(NetworkType.WIFI, connectivity.connectivityStatus.networkType)
    }

    @Test
    fun shouldAttemptDeliveryWhenActiveNetworkIsUnavailableFallsBackToUnknown() {
        val context = mock<Context>()
        val connectivityManager = mock<ConnectivityManager>()

        whenever(connectivityManager.activeNetwork).thenReturn(null)

        val connectivity = ConnectivityApi24(context, connectivityManager, null)

        assertTrue(connectivity.shouldAttemptDelivery())
        assertEquals(NetworkType.UNKNOWN, connectivity.connectivityStatus.networkType)
    }

    private fun testTransportType(
        transportType: Int,
        expectedNetworkType: NetworkType,
    ) {
        val context = mock<Context>()
        val connectivityManager = mock<ConnectivityManager>()
        var callbackInvoked = false

        val connectivity =
            ConnectivityApi24(context, connectivityManager) { status ->
                assertEquals(expectedNetworkType, status.networkType)
                callbackInvoked = true
            }

        whenever(connectivityManager.activeNetwork).thenReturn(mock())

        val networkCapabilities = mock<NetworkCapabilities>()
        whenever(networkCapabilities.hasTransport(transportType))
            .thenReturn(true)
        whenever(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            .thenReturn(true)
        whenever(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            .thenReturn(true)

        whenever(connectivityManager.getNetworkCapabilities(any()))
            .thenReturn(networkCapabilities)

        connectivity.onAvailable(mock())

        assertTrue("Network callback not invoked", callbackInvoked)
    }
}
