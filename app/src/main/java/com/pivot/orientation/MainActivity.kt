package com.pivot.orientation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val cards = LinkedHashMap<Mode, View>()
    private lateinit var phone: PhoneView
    private lateinit var caption: TextView
    private lateinit var modeName: TextView
    private lateinit var power: TextView
    private lateinit var setupCard: View
    private lateinit var setupText: TextView
    private lateinit var setupAction: TextView
    private var pendingMode: Mode? = null

    // Kept as a field: SharedPreferences only holds listeners weakly.
    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render(true) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsets.CONSUMED
        }

        phone = findViewById(R.id.phone)
        caption = findViewById(R.id.caption)
        modeName = findViewById(R.id.mode_name)
        power = findViewById(R.id.power)
        setupCard = findViewById(R.id.setup_card)
        setupText = findViewById(R.id.setup_text)
        setupAction = findViewById(R.id.setup_action)

        power.setOnClickListener {
            if (isActive()) Rotator.stop(this) else select(Prefs.mode(this))
        }
        setupAction.setOnClickListener {
            when (nextSetupStep()) {
                STEP_OVERLAY -> requestOverlayPermission()
                STEP_NOTIF -> askNotifications()
                STEP_BATTERY -> askBattery()
            }
        }

        buildCards(animateIn = savedInstanceState == null)
        render(false)
    }

    override fun onStart() {
        super.onStart()
        Prefs.store(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the "Display over other apps" screen: finish what the user tapped.
        val pending = pendingMode
        pendingMode = null
        if (pending != null && Settings.canDrawOverlays(this)) {
            select(pending)
        }
        render(false)
    }

    override fun onStop() {
        Prefs.store(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATIONS) {
            // Re-post the bar now that notifications are allowed.
            if (hasNotificationPermission() && Prefs.enabled(this)) {
                Rotator.start(this, null)
            }
            render(false)
        }
    }

    // ---- Actions ---------------------------------------------------------------------------

    private fun select(mode: Mode) {
        if (!Settings.canDrawOverlays(this)) {
            pendingMode = mode
            requestOverlayPermission()
            return
        }
        window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        Rotator.start(this, mode)
        if (!hasNotificationPermission() && !Prefs.notifAsked(this)) {
            askNotifications()
        }
    }

    private fun isActive(): Boolean =
        Prefs.enabled(this) && Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        Toast.makeText(this, R.string.toast_overlay, Toast.LENGTH_LONG).show()
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (e2: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        val blockedForGood = Prefs.notifAsked(this) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        if (blockedForGood) {
            // The system will no longer show the dialog, so send the user to the settings page.
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } else {
            Prefs.setNotifAsked(this)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
    }

    private fun askBattery() {
        Prefs.setBatteryAsked(this)
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                // No settings screen available on this phone; nothing more to do.
            }
        }
        render(false)
    }

    private fun isIgnoringBatteryLimits(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun nextSetupStep(): Int = when {
        !Settings.canDrawOverlays(this) -> STEP_OVERLAY
        !hasNotificationPermission() -> STEP_NOTIF
        !isIgnoringBatteryLimits() && !Prefs.batteryAsked(this) -> STEP_BATTERY
        else -> STEP_NONE
    }

    // ---- UI --------------------------------------------------------------------------------

    private fun buildCards(animateIn: Boolean) {
        val grid = findViewById<LinearLayout>(R.id.grid)
        grid.removeAllViews()
        cards.clear()
        val inflater = LayoutInflater.from(this)
        val gap = dp(10)
        var index = 0

        Mode.values().toList().chunked(3).forEachIndexed { rowIndex, rowModes ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val rowParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (rowIndex > 0) rowParams.topMargin = gap
            grid.addView(row, rowParams)

            rowModes.forEachIndexed { colIndex, mode ->
                val card = inflater.inflate(R.layout.item_mode, row, false)
                if (colIndex > 0) {
                    (card.layoutParams as LinearLayout.LayoutParams).marginStart = gap
                }
                card.findViewById<ImageView>(R.id.icon).setImageResource(mode.icon)
                card.findViewById<TextView>(R.id.label).setText(mode.label)
                card.setOnClickListener { select(mode) }
                row.addView(card)
                cards[mode] = card

                if (animateIn) {
                    card.alpha = 0f
                    card.translationY = dp(18).toFloat()
                    card.animate().alpha(1f).translationY(0f)
                        .setStartDelay(120L + index * 55L).setDuration(380).start()
                }
                index++
            }
        }
    }

    private fun render(animate: Boolean) {
        val active = isActive()
        val mode = Prefs.mode(this)

        phone.setState(mode, active, animate)
        caption.setText(if (active) R.string.cap_on else R.string.cap_off)
        modeName.setText(if (active) mode.label else R.string.name_off)
        phone.contentDescription = getString(
            if (active) R.string.status_on else R.string.status_off,
            getString(mode.label)
        )

        power.isSelected = active
        power.setText(if (active) R.string.btn_off else R.string.btn_on)

        for ((m, card) in cards) {
            card.isSelected = active && m == mode
        }

        val step = nextSetupStep()
        if (step == STEP_NONE) {
            setupCard.visibility = View.GONE
        } else {
            setupText.setText(
                when (step) {
                    STEP_OVERLAY -> R.string.setup_overlay
                    STEP_NOTIF -> R.string.setup_notif
                    else -> R.string.setup_battery
                }
            )
            setupCard.visibility = View.VISIBLE
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQ_NOTIFICATIONS = 1
        const val STEP_NONE = 0
        const val STEP_OVERLAY = 1
        const val STEP_NOTIF = 2
        const val STEP_BATTERY = 3
    }
}
