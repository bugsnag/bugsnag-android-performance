package com.bugsnag.android.performance.internal

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.os.RemoteException
import android.telephony.TelephonyManager
import com.bugsnag.android.performance.Logger

internal const val RELEASE_STAGE_DEVELOPMENT = "development"
internal const val RELEASE_STAGE_PRODUCTION = "production"

/**
 * Calls [Context.registerReceiver] but swallows [SecurityException] and [RemoteException]
 * to avoid terminating the process in rare cases where the registration is unsuccessful.
 */
internal fun Context.registerReceiverSafe(
    receiver: BroadcastReceiver?,
    filter: IntentFilter?
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
internal fun Context.unregisterReceiverSafe(
    receiver: BroadcastReceiver?
) {
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

@JvmName("getActivityManagerFrom")
internal fun Context.getActivityManager(): ActivityManager? =
    safeGetSystemService(Context.ACTIVITY_SERVICE)

@JvmName("getConnectivityManagerFrom")
internal fun Context.getConnectivityManager(): ConnectivityManager? =
    safeGetSystemService(Context.CONNECTIVITY_SERVICE)

@JvmName("getTelephonyManagerFrom")
internal fun Context.getTelephonyManager(): TelephonyManager? =
    safeGetSystemService(Context.TELEPHONY_SERVICE)

internal val Context.releaseStage: String
    get() {
        val appInfo = applicationInfo ?: return RELEASE_STAGE_PRODUCTION
        return if (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) RELEASE_STAGE_DEVELOPMENT
        else RELEASE_STAGE_PRODUCTION
    }
