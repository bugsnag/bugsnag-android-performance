package com.bugsnag.android.performance.internal.platform

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build

/**
 * Abstract `ContentProvider` implementation to reduce the boilerplate for implementors only
 * concerned with `init` and `onCreate`.
 */
abstract class AbstractCreateProvider : ContentProvider() {
    final override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        enforcePrivilegeEscalation()
        return null
    }

    final override fun getType(uri: Uri): String? {
        enforcePrivilegeEscalation()
        return null
    }

    final override fun insert(uri: Uri, values: ContentValues?): Uri? {
        enforcePrivilegeEscalation()
        return null
    }

    final override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        enforcePrivilegeEscalation()
        return 0
    }

    final override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        enforcePrivilegeEscalation()
        return 0
    }

    @SuppressLint("NewApi")
    open fun enforcePrivilegeEscalation() {
        val ctx = context ?: return

        val sdk: Int = Build.VERSION.SDK_INT
        if (sdk in Build.VERSION_CODES.O..Build.VERSION_CODES.P) {
            val callingPackage = callingPackage
            val appPackage = ctx.packageName
            if (callingPackage != null && callingPackage == appPackage) {
                return
            }

            throw SecurityException()
        }
    }
}
