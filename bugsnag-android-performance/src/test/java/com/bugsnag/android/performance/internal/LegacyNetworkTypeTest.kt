package com.bugsnag.android.performance.internal

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

@Suppress("DEPRECATION")
class LegacyNetworkTypeTest {
    @Test
    fun legacyNetworkType() {
        testTransportTye(
            ConnectivityManager.TYPE_WIFI,
            NetworkType.WIFI,
            ConnectionMetering.UNMETERED
        )
        testTransportTye(
            ConnectivityManager.TYPE_MOBILE,
            NetworkType.CELL,
            ConnectionMetering.POTENTIALLY_METERED
        )
        testTransportTye(
            ConnectivityManager.TYPE_ETHERNET,
            NetworkType.WIRED,
            ConnectionMetering.UNMETERED
        )
        testTransportTye(
            ConnectivityManager.TYPE_BLUETOOTH,
            NetworkType.UNKNOWN,
            ConnectionMetering.POTENTIALLY_METERED
        )
    }

    private fun testTransportTye(
        transportType: Int,
        expectedNetworkType: NetworkType,
        expectedMetering: ConnectionMetering
    ) {
        val context = mock<Context>()
        var callbackInvoked = false
        val connectivity = ConnectivityLegacy(context, mock()) { _, metering, networkType, _ ->
            assertEquals(expectedNetworkType, networkType)
            assertEquals(expectedMetering, metering)
            callbackInvoked = true
        }

        val intent = mock<Intent> {
            on { it.getParcelableExtra<android.net.NetworkInfo>(eq(ConnectivityManager.EXTRA_NETWORK_INFO)) } doReturn
                newNetworkInfo(transportType, null)
        }

        connectivity.onReceive(context, intent) // first is always ignored
        connectivity.onReceive(context, intent)

        assertTrue("Network callback not invoked", callbackInvoked)
    }

    private fun newNetworkInfo(type: Int, subType: String?): android.net.NetworkInfo =
        object : android.net.NetworkInfo(type, 0, null, null) {
            override fun isConnectedOrConnecting(): Boolean = true
            override fun getType(): Int = type
            override fun getSubtypeName(): String? = subType
        }
}
