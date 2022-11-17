package com.bugsnag.android.performance.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.bugsnag.android.performance.NetworkType
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class Api24NetworkTypeTest {
    @Test
    fun wired() {
        testTransportTye(NetworkCapabilities.TRANSPORT_ETHERNET, NetworkType.WIRED)
        testTransportTye(NetworkCapabilities.TRANSPORT_USB, NetworkType.WIRED)
    }

    @Test
    fun wifi() {
        testTransportTye(NetworkCapabilities.TRANSPORT_WIFI, NetworkType.WIFI)
    }

    @Test
    fun cell() {
        testTransportTye(NetworkCapabilities.TRANSPORT_CELLULAR, NetworkType.CELL)
    }

    private fun testTransportTye(transportType: Int, networkType: NetworkType) {
        val context = mock<Context>()
        val connectivityManager = mock<ConnectivityManager>()

        val connectivity = ConnectivityApi24(context, connectivityManager) { _, _ -> }

        val networkCapabilities = mock<NetworkCapabilities>()
        whenever(networkCapabilities.hasTransport(eq(transportType)))
            .thenReturn(true)

        whenever(connectivityManager.getNetworkCapabilities(isNull()))
            .thenReturn(networkCapabilities)

        Assert.assertEquals(networkType, connectivity.networkType)
    }
}
