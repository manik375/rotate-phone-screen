package com.pivot.orientation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Brings the orientation lock back after a reboot or an app update, if it was on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!Prefs.enabled(context) || !Settings.canDrawOverlays(context)) return
        try {
            Rotator.start(context, null)
        } catch (e: Exception) {
            Prefs.setEnabled(context, false)
        }
    }
}
