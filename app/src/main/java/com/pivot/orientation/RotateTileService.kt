package com.pivot.orientation

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: one tap turns the lock on (last mode) or off. */
class RotateTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        update()
    }

    override fun onClick() {
        super.onClick()
        if (Prefs.enabled(this) && Settings.canDrawOverlays(this)) {
            Rotator.stop(this)
        } else if (!Settings.canDrawOverlays(this)) {
            openApp()
            return
        } else {
            try {
                Rotator.start(this, null)
            } catch (e: Exception) {
                Prefs.setEnabled(this, false)
                openApp()
                return
            }
        }
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val on = Prefs.enabled(this) && Settings.canDrawOverlays(this)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.subtitle = if (on) getString(Prefs.mode(this).label) else getString(R.string.tile_off)
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
