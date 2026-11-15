package com.pivot.orientation

import android.content.Context
import android.content.SharedPreferences

/** Tiny wrapper around SharedPreferences. The service writes, the UI listens. */
object Prefs {
    private const val FILE = "pivot"
    private const val KEY_MODE = "mode"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_NOTIF_ASKED = "notif_asked"

    fun store(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun mode(context: Context): Mode =
        Mode.fromId(store(context).getString(KEY_MODE, null)) ?: Mode.AUTO

    fun setMode(context: Context, mode: Mode) {
        store(context).edit().putString(KEY_MODE, mode.id).apply()
    }

    fun enabled(context: Context): Boolean =
        store(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        store(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun notifAsked(context: Context): Boolean =
        store(context).getBoolean(KEY_NOTIF_ASKED, false)

    fun setNotifAsked(context: Context) {
        store(context).edit().putBoolean(KEY_NOTIF_ASKED, true).apply()
    }
}
