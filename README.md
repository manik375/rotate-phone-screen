# Pivot

A tiny Android app that holds your screen in any orientation: portrait, landscape, upside down, landscape flipped, auto (all four directions) or locked as-is. Switch from a one-tap bar in the notification shade.

- Android 11 and above (minSdk 30, targetSdk 35)
- Native Kotlin, zero dependencies, no internet permission
- Follows system light/dark theme

## How it works

Pivot keeps a 1x1 invisible overlay window alive that requests a fixed `screenOrientation`. Android gives the top-most window's orientation request priority, so it overrides the system auto-rotate setting and any app that tries to force its own. A foreground service owns the window and shows the notification bar.

## Get the APK

### Option A: GitHub (works from a phone browser, no Android Studio)
1. Create a GitHub repo and upload this whole folder (keep the `.github` folder).
2. Open the **Actions** tab, run **Build APK** (it also runs on every push).
3. When it finishes, download the **Pivot-apk** artifact, unzip it, install `app-release.apk`.

### Option B: Android Studio
Open this folder, let Gradle sync, then **Build > Build APK(s)** or press Run with your phone connected.

## First run

1. Tap any mode. Android opens the "Display over other apps" list: switch **Pivot** on, then go back.
2. Allow notifications when asked (needed for the quick bar).
3. Pull down the notification shade and tap an icon: Auto, Portrait, Landscape, Upside down, Landscape flipped, Lock current, Off.

You can also add the **Pivot** tile to Quick Settings to turn the lock on/off in one tap. If the lock was on, it comes back after a reboot or app update.

## Notes for some phones

Xiaomi / Oppo / Vivo / Samsung can be aggressive about background apps. If the bar disappears, set Pivot's battery usage to **Unrestricted** and allow it to "display pop-up windows" / "show on top" in app permissions.
