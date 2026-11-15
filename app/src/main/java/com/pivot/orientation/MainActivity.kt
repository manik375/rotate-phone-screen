package com.pivot.orientation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val cards = LinkedHashMap<Mode, View>()
    private lateinit var masterCard: View
    private lateinit var toggle: View
    private lateinit var knob: View
    private lateinit var status: TextView
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

        // Keep the layout readable on tablets and in landscape.
        val content = findViewById<View>(R.id.content)
        val maxWidth = dp(560)
        if (resources.displayMetrics.widthPixels > maxWidth) {
            (content.layoutParams as FrameLayout.LayoutParams).width = maxWidth
        }

        masterCard = findViewById(R.id.master_card)
        toggle = findViewById(R.id.toggle)
        knob = findViewById(R.id.knob)
        status = findViewById(R.id.status)
        setupCard = findViewById(R.id.setup_card)
        setupText = findViewById(R.id.setup_text)
        setupAction = findViewById(R.id.setup_action)

        masterCard.setOnClickListener {
            if (isActive()) Rotator.stop(this) else select(Prefs.mode(this))
        }
        setupAction.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) requestOverlayPermission() else askNotifications()
        }

        buildCards()
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

    // ---- UI --------------------------------------------------------------------------------

    private fun buildCards() {
        val grid = findViewById<LinearLayout>(R.id.grid)
        val inflater = LayoutInflater.from(this)
        val gap = dp(12)

        Mode.values().toList().chunked(2).forEachIndexed { rowIndex, rowModes ->
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
                card.findViewById<TextView>(R.id.sub).setText(mode.sub)
                card.setOnClickListener { select(mode) }
                row.addView(card)
                cards[mode] = card
            }
        }
    }

    private fun render(animate: Boolean) {
        val active = isActive()
        val mode = Prefs.mode(this)

        status.text = if (active) {
            getString(R.string.status_on, getString(mode.label))
        } else {
            getString(R.string.status_off)
        }
        masterCard.contentDescription = getString(R.string.switch_title) + ", " +
            getString(if (active) R.string.state_on else R.string.state_off)

        toggle.isSelected = active
        val direction = if (toggle.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f
        val target = if (active) dp(22) * direction else 0f
        if (animate) {
            knob.animate().translationX(target).setDuration(160).start()
        } else {
            knob.translationX = target
        }

        for ((m, card) in cards) {
            card.isSelected = active && m == mode
        }

        if (!Settings.canDrawOverlays(this)) {
            setupText.setText(R.string.setup_overlay)
            setupCard.visibility = View.VISIBLE
        } else if (!hasNotificationPermission()) {
            setupText.setText(R.string.setup_notif)
            setupCard.visibility = View.VISIBLE
        } else {
            setupCard.visibility = View.GONE
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQ_NOTIFICATIONS = 1
    }
}
