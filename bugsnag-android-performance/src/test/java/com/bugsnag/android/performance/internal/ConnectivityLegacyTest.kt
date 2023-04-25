@file:Suppress("DEPRECATION")

package com.bugsnag.android.performance.internal

import android.net.ConnectivityManager
import android.net.NetworkInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(Parameterized::class)
class ConnectivityLegacyTest {
    companion object {
        @JvmStatic
        @Parameterized.Parameters
        internal fun createTestNetworkParameters(): Collection<Pair<ExpectedInfo, NetworkInfo>> {
            return listOf(
                Pair(
                    ExpectedInfo(
                        ConnectionMetering.UNMETERED,
                        NetworkType.WIFI,
                        null,
                    ),
                    mockNetworkInfo(ConnectivityManager.TYPE_WIFI, null),
                ),
                Pair(
                    ExpectedInfo(
                        ConnectionMetering.UNMETERED,
                        NetworkType.WIRED,
                        null,
                    ),
                    mockNetworkInfo(ConnectivityManager.TYPE_ETHERNET, null),
                ),
                Pair(
                    ExpectedInfo(
                        ConnectionMetering.POTENTIALLY_METERED,
                        NetworkType.CELL,
                        "CDMA",
                    ),
                    mockNetworkInfo(ConnectivityManager.TYPE_MOBILE, "CDMA"),
                ),
                Pair(
                    ExpectedInfo(
                        ConnectionMetering.POTENTIALLY_METERED,
                        NetworkType.CELL,
                        "hsdpa",
                    ),
                    mockNetworkInfo(ConnectivityManager.TYPE_MOBILE, "HSDPA+"),
                ),
                Pair(
                    ExpectedInfo(
                        ConnectionMetering.POTENTIALLY_METERED,
                        NetworkType.CELL,
                        "evdo_a",
                    ),
                    mockNetworkInfo(ConnectivityManager.TYPE_MOBILE, "CDMA - EvDo rev. A"),
                ),
                Pair(
                    ExpectedInfo(
                        ConnectionMetering.POTENTIALLY_METERED,
                        NetworkType.CELL,
                        "evdo_0",
                    ),
                    mockNetworkInfo(ConnectivityManager.TYPE_MOBILE, "CDMA - EvDo rev. 0"),
                ),
            )
        }

        private fun mockNetworkInfo(
            type: Int,
            subtypeName: String?,
        ) = mock<NetworkInfo> {
            whenever(it.type) doReturn type
            whenever(it.subtypeName) doReturn subtypeName
        }
    }

    @Parameterized.Parameter
    internal lateinit var testCase: Pair<ExpectedInfo, NetworkInfo>

    @Test
    fun testNetworkProperties() {
        val (expected, networkInfo) = testCase
        val connectivityManager = mock<ConnectivityManager> {
            whenever(it.activeNetworkInfo) doReturn networkInfo
        }

        val connectivity = ConnectivityLegacy(mock(), connectivityManager, null)
        val status = connectivity.connectivityStatus

        assertEquals(expected.metering, status.metering)
        assertEquals(expected.networkType, status.networkType)
        assertEquals(expected.networkSubType, status.networkSubType)
    }

    internal data class ExpectedInfo(
        val metering: ConnectionMetering,
        val networkType: NetworkType,
        val networkSubType: String?,
    )
}
