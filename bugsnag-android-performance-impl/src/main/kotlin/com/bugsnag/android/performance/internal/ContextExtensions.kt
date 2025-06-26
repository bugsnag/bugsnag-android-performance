@file:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)

package com.bugsnag.android.performance.internal

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.RemoteException
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.Logger

public const val RELEASE_STAGE_DEVELOPMENT: String = "development"
public const val RELEASE_STAGE_PRODUCTION: String = "production"

/**
 * Calls [Context.registerReceiver] but swallows [SecurityException] and [RemoteException]
 * to avoid terminating the process in rare cases where the registration is unsuccessful.
 */
public fun Context.registerReceiverSafe(
    receiver: BroadcastReceiver?,
    filter: IntentFilter?,
): Intent? {
    try {
        return registerReceiver(receiver, filter)
    } catch (exc: SecurityException) {
        Logger.w("Failed to register receiver", exc)
    } catch (exc: RemoteException) {
        Logger.w("Failed to register receiver", exc)
    } catch (exc: IllegalArgumentException) {
        Logger.w("Failed to register receiver", exc)
    }
    return null
}

/**
 * Calls [Context.unregisterReceiver] but swallows [SecurityException] and [RemoteException]
 * to avoid terminating the process in rare cases where the registration is unsuccessful.
 */
public fun Context.unregisterReceiverSafe(receiver: BroadcastReceiver?) {
    try {
        unregisterReceiver(receiver)
    } catch (exc: SecurityException) {
        Logger.w("Failed to register receiver", exc)
    } catch (exc: RemoteException) {
        Logger.w("Failed to register receiver", exc)
    } catch (exc: IllegalArgumentException) {
        Logger.w("Failed to register receiver", exc)
    }
}

private inline fun <reified T> Context.safeGetSystemService(name: String): T? {
    return try {
        getSystemService(name) as? T
    } catch (exc: RuntimeException) {
        null
    }
}

/**
 * work around https://issuetracker.google.com/issues/175055271 on Android 11
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public fun ConnectivityManager.safeGetNetworkCapabilities(network: Network?): NetworkCapabilities? {
    return try {
        getNetworkCapabilities(network)
    } catch (exc: SecurityException) {
        null
    }
}

@JvmName("getActivityManagerFrom")
public fun Context.getActivityManager(): ActivityManager? = safeGetSystemService(Context.ACTIVITY_SERVICE)

@JvmName("getConnectivityManagerFrom")
public fun Context.getConnectivityManager(): ConnectivityManager? = safeGetSystemService(Context.CONNECTIVITY_SERVICE)

@JvmName("getTelephonyManagerFrom")
public fun Context.getTelephonyManager(): TelephonyManager? = safeGetSystemService(Context.TELEPHONY_SERVICE)

public val Context.releaseStage: String
    get() {
        val appInfo = applicationInfo ?: return RELEASE_STAGE_PRODUCTION
        return if (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            RELEASE_STAGE_DEVELOPMENT
        } else {
            RELEASE_STAGE_PRODUCTION
        }
    }
