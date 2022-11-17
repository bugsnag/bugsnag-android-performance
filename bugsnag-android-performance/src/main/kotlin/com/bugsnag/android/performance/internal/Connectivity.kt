package com.bugsnag.android.performance.internal

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED
import android.net.NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_USB
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.bugsnag.android.performance.NetworkType
import java.util.concurrent.atomic.AtomicBoolean

private const val READ_BASIC_PHONE_STATE = "android.permission.READ_BASIC_PHONE_STATE"
private const val READ_PHONE_STATE = android.Manifest.permission.READ_PHONE_STATE

internal typealias NetworkChangeCallback = (hasConnection: Boolean, metering: ConnectionMetering) -> Unit

internal enum class ConnectionMetering {
    DISCONNECTED,
    UNMETERED,
    POTENTIALLY_METERED,
}

internal interface Connectivity {
    val networkType: NetworkType
    val networkSubType: String?

    fun registerForNetworkChanges()
    fun unregisterForNetworkChanges()
    fun hasNetworkConnection(): Boolean
    fun metering(): ConnectionMetering
}

internal class ConnectivityCompat(
    context: Context,
    callback: NetworkChangeCallback?
) : Connectivity {

    private val cm = context.getConnectivityManager()

    private val connectivity: Connectivity =
        when {
            cm == null -> UnknownConnectivity
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                ConnectivityApi31(context, cm, callback)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ->
                ConnectivityApi24(context, cm, callback)
            else -> ConnectivityLegacy(context, cm, callback)
        }

    override val networkType: NetworkType
        get() = connectivity.networkType

    override val networkSubType: String?
        get() = connectivity.networkSubType

    override fun registerForNetworkChanges() {
        runCatching { connectivity.registerForNetworkChanges() }
    }

    override fun hasNetworkConnection(): Boolean {
        val result = runCatching { connectivity.hasNetworkConnection() }
        return result.getOrElse { true } // allow network requests to be made if state unknown
    }

    override fun unregisterForNetworkChanges() {
        runCatching { connectivity.unregisterForNetworkChanges() }
    }

    override fun metering(): ConnectionMetering {
        val result = runCatching { connectivity.metering() }
        return result.getOrElse { ConnectionMetering.DISCONNECTED }
    }
}

@Suppress("DEPRECATION")
internal class ConnectivityLegacy(
    private val context: Context,
    private val cm: ConnectivityManager,
    private val callback: NetworkChangeCallback?
) : BroadcastReceiver(), Connectivity {

    private val receivedFirstCallback = AtomicBoolean(false)

    private val activeNetworkInfo: android.net.NetworkInfo?
        get() = try {
            cm.activeNetworkInfo
        } catch (e: NullPointerException) {
            // in some rare cases we get a remote NullPointerException via Parcel.readException
            null
        }

    override val networkType: NetworkType
        get() = when (activeNetworkInfo?.type) {
            null -> NetworkType.UNAVAILABLE
            ConnectivityManager.TYPE_WIFI -> NetworkType.WIFI
            ConnectivityManager.TYPE_MOBILE -> NetworkType.CELL
            ConnectivityManager.TYPE_ETHERNET -> NetworkType.WIRED
            else -> NetworkType.UNKNOWN
        }

    override val networkSubType: String?
        get() = activeNetworkInfo?.subtypeName

    override fun registerForNetworkChanges() {
        val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        context.registerReceiverSafe(this, intentFilter)
    }

    override fun unregisterForNetworkChanges() = context.unregisterReceiverSafe(this)

    override fun hasNetworkConnection(): Boolean {
        return activeNetworkInfo?.isConnectedOrConnecting ?: false
    }

    override fun metering(): ConnectionMetering {
        return when (activeNetworkInfo?.type) {
            null -> ConnectionMetering.DISCONNECTED
            ConnectivityManager.TYPE_WIFI -> ConnectionMetering.UNMETERED
            ConnectivityManager.TYPE_ETHERNET -> ConnectionMetering.UNMETERED
            else -> ConnectionMetering.POTENTIALLY_METERED // all other types are cellular in some form
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // don't send a callback for the first state-update event
        if (receivedFirstCallback.getAndSet(true)) {
            callback?.invoke(hasNetworkConnection(), metering())
        }
    }
}

@RequiresApi(Build.VERSION_CODES.N)
internal open class ConnectivityApi24(
    private val context: Context,
    internal val cm: ConnectivityManager,
    private val callback: NetworkChangeCallback?
) : ConnectivityManager.NetworkCallback(), Connectivity {

    private val receivedFirstCallback = AtomicBoolean(false)
    private val tm: TelephonyManager? = context.getTelephonyManager()

    override val networkType: NetworkType
        get() {
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
            return when {
                capabilities == null -> NetworkType.UNAVAILABLE
                capabilities.hasTransport(TRANSPORT_ETHERNET) ||
                    capabilities.hasTransport(TRANSPORT_USB) -> NetworkType.WIRED
                capabilities.hasTransport(TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(TRANSPORT_CELLULAR) -> NetworkType.CELL
                else -> NetworkType.UNKNOWN
            }
        }

    override val networkSubType: String?
        @SuppressLint("MissingPermission")
        get() {
            if (networkType != NetworkType.CELL || tm == null) {
                return null
            }

            if (context.checkCallingPermission(READ_BASIC_PHONE_STATE) == PERMISSION_GRANTED
                || context.checkCallingPermission(READ_PHONE_STATE) == PERMISSION_GRANTED
            ) {
                return when (tm.dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
                    TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
                    TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
                    TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
                    TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
                    TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
                    TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
                    TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO_0"
                    TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO_A"
                    TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO_B"
                    TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
                    TelephonyManager.NETWORK_TYPE_IDEN -> "IDEN"
                    TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                    TelephonyManager.NETWORK_TYPE_EHRPD -> "EHRPD"
                    TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPAP"
                    TelephonyManager.NETWORK_TYPE_NR -> "NR"
                    else -> "UNKNOWN"
                }
            }

            return null
        }

    override fun registerForNetworkChanges() = cm.registerDefaultNetworkCallback(this)
    override fun unregisterForNetworkChanges() = cm.unregisterNetworkCallback(this)
    override fun hasNetworkConnection() = cm.activeNetwork != null

    override fun metering(): ConnectionMetering {
        val network = cm.activeNetwork
        val capabilities = if (network != null) cm.getNetworkCapabilities(network) else null

        return when {
            capabilities == null -> ConnectionMetering.DISCONNECTED
            capabilities.hasTransport(TRANSPORT_WIFI) -> ConnectionMetering.UNMETERED
            capabilities.hasTransport(TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(TRANSPORT_USB) -> ConnectionMetering.UNMETERED
            capabilities.hasTransport(TRANSPORT_CELLULAR) -> ConnectionMetering.POTENTIALLY_METERED
            else -> ConnectionMetering.DISCONNECTED
        }
    }

    override fun onUnavailable() {
        super.onUnavailable()
        invokeNetworkCallback(false)
    }

    override fun onAvailable(network: Network) {
        super.onAvailable(network)
        val capabilities = cm.getNetworkCapabilities(network) ?: return
        if (capabilities.hasCapability(NET_CAPABILITY_INTERNET)) {
            invokeNetworkCallback(true)
        }
    }

    /**
     * Invokes the network callback, as long as the ConnectivityManager callback has been
     * triggered at least once before (when setting a NetworkCallback Android always
     * invokes the callback with the current network state).
     */
    private fun invokeNetworkCallback(hasConnection: Boolean) {
        if (receivedFirstCallback.getAndSet(true)) {
            callback?.invoke(hasConnection, UnknownConnectivity.metering())
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
internal class ConnectivityApi31(
    context: Context,
    cm: ConnectivityManager,
    callback: NetworkChangeCallback?
) : ConnectivityApi24(context, cm, callback) {

    override fun metering(): ConnectionMetering {
        val network = cm.activeNetwork
        val capabilities = if (network != null) cm.getNetworkCapabilities(network) else null

        return when {
            capabilities == null -> ConnectionMetering.DISCONNECTED
            capabilities.hasCapability(NET_CAPABILITY_NOT_METERED) ||
                capabilities.hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED) ->
                ConnectionMetering.UNMETERED
            else -> super.metering()
        }
    }
}

/**
 * Connectivity used in cases where we cannot access the system ConnectivityManager.
 * We assume that there is some sort of network and do not attempt to report any network changes.
 */
internal object UnknownConnectivity : Connectivity {
    override val networkType: NetworkType get() = NetworkType.UNKNOWN
    override val networkSubType: String? get() = null

    override fun registerForNetworkChanges() = Unit
    override fun unregisterForNetworkChanges() = Unit
    override fun hasNetworkConnection(): Boolean = true
    override fun metering(): ConnectionMetering = ConnectionMetering.DISCONNECTED
}
