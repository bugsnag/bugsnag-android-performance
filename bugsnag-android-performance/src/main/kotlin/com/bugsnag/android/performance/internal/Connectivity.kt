package com.bugsnag.android.performance.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import java.util.concurrent.atomic.AtomicBoolean

internal typealias NetworkChangeCallback = (hasConnection: Boolean, metering: ConnectionMetering) -> Unit

internal enum class ConnectionMetering {
    DISCONNECTED,
    UNMETERED,
    POTENTIALLY_METERED,
}

internal interface Connectivity {
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
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> ConnectivityApi31(cm, callback)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> ConnectivityApi24(cm, callback)
            else -> ConnectivityLegacy(context, cm, callback)
        }

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
    callback: NetworkChangeCallback?
) : Connectivity {

    private val changeReceiver = ConnectivityChangeReceiver(callback)

    private val activeNetworkInfo: android.net.NetworkInfo?
        get() = try {
            cm.activeNetworkInfo
        } catch (e: NullPointerException) {
            // in some rare cases we get a remote NullPointerException via Parcel.readException
            null
        }

    override fun registerForNetworkChanges() {
        val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        context.registerReceiverSafe(changeReceiver, intentFilter)
    }

    override fun unregisterForNetworkChanges() = context.unregisterReceiverSafe(changeReceiver)

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

    private inner class ConnectivityChangeReceiver(
        private val cb: NetworkChangeCallback?
    ) : BroadcastReceiver() {

        private val receivedFirstCallback = AtomicBoolean(false)

        override fun onReceive(context: Context, intent: Intent) {
            if (receivedFirstCallback.getAndSet(true)) {
                cb?.invoke(hasNetworkConnection(), metering())
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.N)
internal open class ConnectivityApi24(
    internal val cm: ConnectivityManager,
    callback: NetworkChangeCallback?
) : Connectivity {

    private val networkCallback = ConnectivityTrackerCallback(callback)

    override fun registerForNetworkChanges() = cm.registerDefaultNetworkCallback(networkCallback)
    override fun unregisterForNetworkChanges() = cm.unregisterNetworkCallback(networkCallback)
    override fun hasNetworkConnection() = cm.activeNetwork != null

    override fun metering(): ConnectionMetering {
        val network = cm.activeNetwork
        val capabilities = if (network != null) cm.getNetworkCapabilities(network) else null

        return when {
            capabilities == null -> ConnectionMetering.DISCONNECTED
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionMetering.UNMETERED
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionMetering.UNMETERED
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionMetering.POTENTIALLY_METERED
            else -> ConnectionMetering.DISCONNECTED
        }
    }

    @VisibleForTesting
    internal class ConnectivityTrackerCallback(
        private val cb: NetworkChangeCallback?
    ) : ConnectivityManager.NetworkCallback() {

        private val receivedFirstCallback = AtomicBoolean(false)

        override fun onUnavailable() {
            super.onUnavailable()
            invokeNetworkCallback(false)
        }

        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            invokeNetworkCallback(true)
        }

        /**
         * Invokes the network callback, as long as the ConnectivityManager callback has been
         * triggered at least once before (when setting a NetworkCallback Android always
         * invokes the callback with the current network state).
         */
        private fun invokeNetworkCallback(hasConnection: Boolean) {
            if (receivedFirstCallback.getAndSet(true)) {
                cb?.invoke(hasConnection, UnknownConnectivity.metering())
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
internal class ConnectivityApi31(
    cm: ConnectivityManager,
    callback: NetworkChangeCallback?
) : ConnectivityApi24(cm, callback) {

    override fun metering(): ConnectionMetering {
        val network = cm.activeNetwork
        val capabilities = if (network != null) cm.getNetworkCapabilities(network) else null

        return when {
            capabilities == null -> ConnectionMetering.DISCONNECTED
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED)
            -> ConnectionMetering.UNMETERED
            else -> super.metering()
        }
    }
}

/**
 * Connectivity used in cases where we cannot access the system ConnectivityManager.
 * We assume that there is some sort of network and do not attempt to report any network changes.
 */
internal object UnknownConnectivity : Connectivity {
    override fun registerForNetworkChanges() = Unit
    override fun unregisterForNetworkChanges() = Unit
    override fun hasNetworkConnection(): Boolean = true
    override fun metering(): ConnectionMetering = ConnectionMetering.DISCONNECTED
}
