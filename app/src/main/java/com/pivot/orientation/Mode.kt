package com.pivot.orientation

import android.content.pm.ActivityInfo

/**
 * Every orientation Pivot can hold. [barId] is the button in the notification bar.
 */
enum class Mode(
    val id: String,
    val orientation: Int,
    val label: Int,
    val sub: Int,
    val icon: Int,
    val barId: Int
) {
    AUTO(
        "auto", ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
        R.string.mode_auto, R.string.sub_auto, R.drawable.ic_auto, R.id.btn_auto
    ),
    PORTRAIT(
        "portrait", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        R.string.mode_portrait, R.string.sub_portrait, R.drawable.ic_portrait, R.id.btn_portrait
    ),
    LANDSCAPE(
        "landscape", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        R.string.mode_landscape, R.string.sub_landscape, R.drawable.ic_landscape, R.id.btn_landscape
    ),
    PORTRAIT_FLIP(
        "portrait_flip", ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
        R.string.mode_portrait_flip, R.string.sub_portrait_flip, R.drawable.ic_portrait_flip, R.id.btn_portrait_flip
    ),
    LANDSCAPE_FLIP(
        "landscape_flip", ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
        R.string.mode_landscape_flip, R.string.sub_landscape_flip, R.drawable.ic_landscape_flip, R.id.btn_landscape_flip
    ),
    LOCK(
        "lock", ActivityInfo.SCREEN_ORIENTATION_LOCKED,
        R.string.mode_lock, R.string.sub_lock, R.drawable.ic_lock, R.id.btn_lock
    );

    companion object {
        fun fromId(id: String?): Mode? = values().firstOrNull { it.id == id }
    }
}
