package com.bugsnag.android.performance.internal

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED
import android.net.NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_USB
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi

internal data class ConnectivityStatus(
    val hasConnection: Boolean,
    val metering: ConnectionMetering,
    val networkType: NetworkType,
    val networkSubType: String?,
)

private val unknownNetwork = ConnectivityStatus(
    false,
    ConnectionMetering.DISCONNECTED,
    NetworkType.UNKNOWN,
    null,
)

private val noNetwork = ConnectivityStatus(
    false,
    ConnectionMetering.DISCONNECTED,
    NetworkType.UNAVAILABLE,
    null,
)

internal typealias NetworkChangeCallback = (status: ConnectivityStatus) -> Unit

internal enum class ConnectionMetering {
    DISCONNECTED,
    UNMETERED,
    POTENTIALLY_METERED,
}

internal interface Connectivity {

    val connectivityStatus: ConnectivityStatus

    fun registerForNetworkChanges()
    fun unregisterForNetworkChanges()

    companion object Factory {
        fun newInstance(
            context: Context,
            callback: NetworkChangeCallback?,
        ): Connectivity {
            val cm = context.getConnectivityManager()

            return when {
                cm == null -> UnknownConnectivity
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    ConnectivityApi31(context, cm, callback)

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ->
                    ConnectivityApi24(context, cm, callback)

                else -> ConnectivityLegacy(context, cm, callback)
            }
        }
    }
}

@Suppress("DEPRECATION")
internal class ConnectivityLegacy(
    private val context: Context,
    private val cm: ConnectivityManager,
    private val callback: NetworkChangeCallback?,
) : BroadcastReceiver(), Connectivity {
    override var connectivityStatus: ConnectivityStatus = networkInfoToStatus(cm.activeNetworkInfo)
        private set(newStatus) {
            if (field != newStatus) {
                field = newStatus
                callback?.invoke(newStatus)
            }
        }

    override fun registerForNetworkChanges() {
        runCatching {
            val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            context.registerReceiverSafe(this, intentFilter)
        }
    }

    override fun unregisterForNetworkChanges() {
        runCatching {
            context.unregisterReceiverSafe(this)
        }
    }

    private fun networkInfoToStatus(info: android.net.NetworkInfo?): ConnectivityStatus {
        if (info == null) {
            return noNetwork
        }

        val subtype = info.subtypeName
        return ConnectivityStatus(
            info.isConnectedOrConnecting,
            when (info.type) {
                ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_ETHERNET ->
                    ConnectionMetering.UNMETERED

                else -> ConnectionMetering.POTENTIALLY_METERED
            },
            when (info.type) {
                ConnectivityManager.TYPE_WIFI -> NetworkType.WIFI
                ConnectivityManager.TYPE_MOBILE -> NetworkType.CELL
                ConnectivityManager.TYPE_ETHERNET -> NetworkType.WIRED
                else -> NetworkType.UNKNOWN
            },
            when (subtype?.lowercase()) {
                "hsdpa+" -> "hsdpa"
                "cdma - evdo rev. 0" -> "evdo_0"
                "cdma - evdo rev. a" -> "evdo_a"
                "cdma - 1xrtt" -> "cdma2000_1xrtt"
                else -> subtype
            },
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        connectivityStatus = networkInfoToStatus(cm.activeNetworkInfo)
    }
}

@RequiresApi(Build.VERSION_CODES.N)
internal open class ConnectivityApi24(
    context: Context,
    private val cm: ConnectivityManager,
    private val callback: NetworkChangeCallback?,
) : ConnectivityManager.NetworkCallback(), Connectivity {

    private val tm: TelephonyManager? = context.getTelephonyManager()

    override var connectivityStatus: ConnectivityStatus =
        networkCapabilitiesToStatus(cm.getNetworkCapabilities(cm.activeNetwork))
        protected set(newStatus) {
            if (field != newStatus) {
                field = newStatus
                callback?.invoke(field)
            }
        }

    private fun networkCapabilitiesToStatus(capabilities: NetworkCapabilities?): ConnectivityStatus {
        if (capabilities == null) {
            return unknownNetwork
        }

        return ConnectivityStatus(
            connectedFor(capabilities),
            meteringFor(capabilities),
            networkTypeFor(capabilities),
            networkSubTypeFor(capabilities),
        )
    }

    protected open fun networkTypeFor(capabilities: NetworkCapabilities): NetworkType {
        return when {
            capabilities.hasTransport(TRANSPORT_ETHERNET) ||
                    capabilities.hasTransport(TRANSPORT_USB) -> NetworkType.WIRED

            capabilities.hasTransport(TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(TRANSPORT_CELLULAR) -> NetworkType.CELL
            else -> NetworkType.UNKNOWN
        }
    }

    @SuppressLint("MissingPermission")
    protected open fun networkSubTypeFor(capabilities: NetworkCapabilities): String? {
        if (networkTypeFor(capabilities) != NetworkType.CELL || tm == null) {
            return null
        }

        return try {
            nameForDataNetworkType(tm.dataNetworkType)
        } catch (e: Exception) {
            null
        }
    }

    private fun nameForDataNetworkType(dataNetworkType: Int): String = when (dataNetworkType) {
        TelephonyManager.NETWORK_TYPE_1xRTT -> "cdma2000_1xrtt"
        TelephonyManager.NETWORK_TYPE_CDMA -> "cdma"
        TelephonyManager.NETWORK_TYPE_EDGE -> "edge"
        TelephonyManager.NETWORK_TYPE_EHRPD -> "ehrpd"
        TelephonyManager.NETWORK_TYPE_EVDO_0 -> "evdo_0"
        TelephonyManager.NETWORK_TYPE_EVDO_A -> "evdo_a"
        TelephonyManager.NETWORK_TYPE_EVDO_B -> "evdo_b"
        TelephonyManager.NETWORK_TYPE_GSM -> "gsm"
        TelephonyManager.NETWORK_TYPE_GPRS -> "gprs"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "hsdpa"
        TelephonyManager.NETWORK_TYPE_HSPA -> "hspa"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "hspap"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "hsupa"
        TelephonyManager.NETWORK_TYPE_IDEN -> "iden"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "iwlan"
        TelephonyManager.NETWORK_TYPE_LTE -> "lte"
        TelephonyManager.NETWORK_TYPE_NR -> "nr"
        TelephonyManager.NETWORK_TYPE_UMTS -> "umts"
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "td_scdma"
        else -> "unknown"
    }

    protected open fun meteringFor(capabilities: NetworkCapabilities): ConnectionMetering {
        return when {
            capabilities.hasTransport(TRANSPORT_WIFI) -> ConnectionMetering.UNMETERED
            capabilities.hasTransport(TRANSPORT_ETHERNET) ||
                    capabilities.hasTransport(TRANSPORT_USB) -> ConnectionMetering.UNMETERED

            capabilities.hasTransport(TRANSPORT_CELLULAR) -> ConnectionMetering.POTENTIALLY_METERED
            else -> ConnectionMetering.DISCONNECTED
        }
    }

    protected open fun connectedFor(capabilities: NetworkCapabilities): Boolean {
        return capabilities.hasCapability(NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NET_CAPABILITY_VALIDATED)
    }

    override fun registerForNetworkChanges() {
        runCatching {
            cm.registerDefaultNetworkCallback(this)
        }
    }

    override fun unregisterForNetworkChanges() {
        runCatching {
            cm.unregisterNetworkCallback(this)
        }
    }

    override fun onUnavailable() = updateActiveNetwork()
    override fun onLost(network: Network) = updateActiveNetwork()
    override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
        updateActiveNetwork()

    override fun onAvailable(network: Network) = updateActiveNetwork()
    override fun onBlockedStatusChanged(network: Network, blocked: Boolean) = updateActiveNetwork()
    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
        updateActiveNetwork()

    private fun updateActiveNetwork() {
        val activeNetwork = cm.activeNetwork
        if (activeNetwork == null) {
            connectivityStatus = noNetwork
            return
        }

        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return
        connectivityStatus = networkCapabilitiesToStatus(capabilities)
    }

}

@RequiresApi(Build.VERSION_CODES.S)
internal class ConnectivityApi31(
    context: Context,
    cm: ConnectivityManager,
    callback: NetworkChangeCallback?,
) : ConnectivityApi24(context, cm, callback) {

    override fun meteringFor(capabilities: NetworkCapabilities): ConnectionMetering {
        return when {
            capabilities.hasCapability(NET_CAPABILITY_NOT_METERED) ||
                    capabilities.hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED) -> ConnectionMetering.UNMETERED

            else -> super.meteringFor(capabilities)
        }
    }
}

/**
 * Connectivity used in cases where we cannot access the system ConnectivityManager.
 * We assume that there is some sort of network and do not attempt to report any network changes.
 */
internal object UnknownConnectivity : Connectivity {
    override val connectivityStatus: ConnectivityStatus
        get() = unknownNetwork

    override fun registerForNetworkChanges() = Unit
    override fun unregisterForNetworkChanges() = Unit
}

/**
 * Should we attempt to deliver a payload based on the current status of the `Connectivity`. Returns
 * `true` if the `Connectivity` [hasConnection] *or* is an unknown network (handling edge cases
 * where the network status has not been set yet, or the app might not have appropriate permissions).
 */
internal fun Connectivity.shouldAttemptDelivery(): Boolean =
    connectivityStatus.networkType == NetworkType.UNKNOWN || connectivityStatus.hasConnection
