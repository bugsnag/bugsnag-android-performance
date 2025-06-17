package com.bugsnag.android.performance.internal

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@Suppress("DEPRECATION")
class LegacyNetworkTypeTest {
    @Test
    fun legacyNetworkType() {
        testTransportTye(
            ConnectivityManager.TYPE_WIFI,
            NetworkType.WIFI,
            ConnectionMetering.UNMETERED,
        )
        testTransportTye(
            ConnectivityManager.TYPE_MOBILE,
            NetworkType.CELL,
            ConnectionMetering.POTENTIALLY_METERED,
        )
        testTransportTye(
            ConnectivityManager.TYPE_ETHERNET,
            NetworkType.WIRED,
            ConnectionMetering.UNMETERED,
        )
        testTransportTye(
            ConnectivityManager.TYPE_BLUETOOTH,
            NetworkType.UNKNOWN,
            ConnectionMetering.POTENTIALLY_METERED,
        )
    }

    private fun testTransportTye(
        transportType: Int,
        expectedNetworkType: NetworkType,
        expectedMetering: ConnectionMetering,
    ) {
        val context = mock<Context>()
        var callbackInvoked = false
        val connectivityManager =
            mock<ConnectivityManager> {
                whenever(it.activeNetworkInfo) doReturnConsecutively
                    listOf(
                        null,
                        newNetworkInfo(transportType, null),
                    )
            }

        val connectivity =
            ConnectivityLegacy(context, connectivityManager) { status ->
                assertEquals(expectedNetworkType, status.networkType)
                assertEquals(expectedMetering, status.metering)
                callbackInvoked = true
            }

        connectivity.onReceive(context, Intent())

        assertTrue("Network callback not invoked", callbackInvoked)
    }

    private fun newNetworkInfo(
        type: Int,
        subType: String?,
    ): android.net.NetworkInfo =
        @Suppress("OVERRIDE_DEPRECATION")
        object : android.net.NetworkInfo(type, 0, null, null) {
            override fun isConnectedOrConnecting(): Boolean = true

            override fun getType(): Int = type

            override fun getSubtypeName(): String? = subType
        }
}
