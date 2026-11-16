package com.pivot.orientation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.RemoteViews

/**
 * Holds the orientation with a 1x1 invisible overlay window that requests a fixed
 * screenOrientation, and shows the one-tap orientation bar as a foreground notification.
 */
class RotationService : Service() {

    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var lastNight = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = Mode.fromId(intent?.getStringExtra(EXTRA_MODE)) ?: Prefs.mode(this)

        // A service started with startForegroundService() must call startForeground() promptly.
        try {
            startForeground(NOTIFICATION_ID, buildNotification(mode))
        } catch (e: Exception) {
            shutdown()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }

        if (!applyOrientation(mode)) {
            shutdown()
            return START_NOT_STICKY
        }

        Prefs.setMode(this, mode)
        Prefs.setEnabled(this, true)
        requestTileRefresh()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Re-tint the bar if the system switched between light and dark.
        if (overlay != null && isNight() != lastNight) {
            try {
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification(Prefs.mode(this)))
            } catch (e: Exception) {
                // Nothing to do; the bar refreshes on the next tap.
            }
        }
    }

    override fun onDestroy() {
        removeOverlay()
        requestTileRefresh()
        super.onDestroy()
    }

    // ---- Overlay ---------------------------------------------------------------------------

    private fun applyOrientation(mode: Mode): Boolean {
        val manager = windowManager ?: return false
        if (!Settings.canDrawOverlays(this)) return false
        return try {
            val params = overlayParams
            val view = overlay
            if (params != null && view != null) {
                params.screenOrientation = mode.orientation
                manager.updateViewLayout(view, params)
            } else {
                val newParams = WindowManager.LayoutParams(
                    1,
                    1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                )
                newParams.gravity = Gravity.TOP or Gravity.START
                newParams.screenOrientation = mode.orientation
                val newView = View(this)
                manager.addView(newView, newParams)
                overlay = newView
                overlayParams = newParams
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun removeOverlay() {
        val view = overlay ?: return
        try {
            windowManager?.removeView(view)
        } catch (e: Exception) {
            // Already gone.
        }
        overlay = null
        overlayParams = null
    }

    private fun shutdown() {
        Prefs.setEnabled(this, false)
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- Notification bar ------------------------------------------------------------------

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = getString(R.string.channel_desc)
        channel.setSound(null, null)
        channel.enableVibration(false)
        channel.enableLights(false)
        channel.setShowBadge(false)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun buildNotification(mode: Mode): Notification {
        lastNight = isNight()
        val idleTint = if (lastNight) 0xB3FFFFFF.toInt() else 0x99000000.toInt()

        val views = RemoteViews(packageName, R.layout.notification_bar)
        for (m in Mode.values()) {
            views.setOnClickPendingIntent(m.barId, modePending(m))
            if (m == mode) {
                views.setInt(m.barId, "setBackgroundResource", R.drawable.bg_chip_selected)
                views.setInt(m.barId, "setColorFilter", Color.WHITE)
            } else {
                views.setInt(m.barId, "setBackgroundResource", R.drawable.bg_chip_idle)
                views.setInt(m.barId, "setColorFilter", idleTint)
            }
        }
        views.setOnClickPendingIntent(R.id.btn_off, stopPending())
        views.setInt(R.id.btn_off, "setColorFilter", idleTint)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_auto)
            .setColor(getColor(R.color.accent))
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(mode.label))
            .setContentIntent(openApp)
            // If the bar is swiped away, bring it straight back (the lock keeps running anyway).
            .setDeleteIntent(
                PendingIntent.getService(
                    this,
                    101,
                    Intent(this, RotationService::class.java)
                        .setAction(ACTION_SET)
                        .putExtra(EXTRA_MODE, mode.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setCustomContentView(views)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun modePending(mode: Mode): PendingIntent {
        val intent = Intent(this, RotationService::class.java)
            .setAction(ACTION_SET)
            .putExtra(EXTRA_MODE, mode.id)
        return PendingIntent.getService(
            this,
            mode.ordinal + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopPending(): PendingIntent {
        val intent = Intent(this, RotationService::class.java).setAction(ACTION_STOP)
        return PendingIntent.getService(
            this,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestTileRefresh() {
        try {
            TileService.requestListeningState(
                this,
                ComponentName(this, RotateTileService::class.java)
            )
        } catch (e: Exception) {
            // The tile may not be added; that's fine.
        }
    }

    companion object {
        const val ACTION_SET = "com.pivot.orientation.action.SET"
        const val ACTION_STOP = "com.pivot.orientation.action.STOP"
        const val EXTRA_MODE = "mode"
        private const val CHANNEL_ID = "orientation_bar"
        private const val NOTIFICATION_ID = 1
    }
}
