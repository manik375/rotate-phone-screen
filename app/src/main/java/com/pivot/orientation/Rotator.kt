package com.pivot.orientation

import android.content.Context
import android.content.Intent

/** Single entry point for turning the orientation lock on, switching modes, or off. */
object Rotator {

    /** Turns the lock on (or switches mode). Pass null to resume the saved mode. */
    fun start(context: Context, mode: Mode?) {
        if (mode != null) Prefs.setMode(context, mode)
        Prefs.setEnabled(context, true)
        val intent = Intent(context, RotationService::class.java)
            .setAction(RotationService.ACTION_SET)
        if (mode != null) intent.putExtra(RotationService.EXTRA_MODE, mode.id)
        context.startForegroundService(intent)
    }

    /** Turns the lock off and removes the notification bar. */
    fun stop(context: Context) {
        Prefs.setEnabled(context, false)
        context.stopService(Intent(context, RotationService::class.java))
    }
}
