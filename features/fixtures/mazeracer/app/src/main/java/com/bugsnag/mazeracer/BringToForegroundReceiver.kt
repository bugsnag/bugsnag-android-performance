package com.bugsnag.mazeracer

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Wakes the Maze fixture after an app-driven HOME. Prefer [ActivityManager.AppTask.moveToFront]
 * so we do not start a new launcher/splash task (which would drop in-memory scenario state).
 */
class BringToForegroundReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        log("BringToForegroundReceiver.onReceive()")
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val task = am.appTasks.firstOrNull()
        if (task != null) {
            task.moveToFront()
            return
        }
        log("BringToForegroundReceiver no AppTask; starting MainActivity")
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            },
        )
    }
}
