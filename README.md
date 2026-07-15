# MT Auto Clicker — Chrome OS & Android

System-wide auto clicker for **Chromebooks** (Google Play) and **Android phones/tablets**, matching the MT Auto Clicker desktop and browser extension family.

## Features (v1.0.0)

- **Single Target** — repeat click at one screen position
- **Multi Target** — sequence or parallel clicks at numbered positions
- **Floating control bar** — place targets, play, pause, stop over any app
- **Presets** — save/load automation setups
- **Kill switch** — emergency stop from the home screen
- **Analytics** — optional anonymous tracking to `mtautoclicker.net` (same API as desktop/extension)

## Requirements

- Android 8.0+ (API 26) or Chromebook with Google Play
- **Display over other apps** permission (overlay float bar)
- **Accessibility service** enabled (screen taps at your chosen coordinates)

## Build

```bash
cd mt-autoclicker-android
export ANDROID_HOME="$HOME/Library/Android/sdk"   # adjust if needed
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

Release build:

```bash
./gradlew assembleRelease
```

## Install on Chromebook

1. Enable **Linux / Play Store** is not required — install from Play Store after publishing, or sideload the debug APK for testing.
2. For sideload: Settings → Apps → Manage Google Play → turn on if needed, or use `adb install app-debug.apk`.
3. Open MT Auto Clicker → **Settings** → grant **Overlay** and **Accessibility**.
4. Choose **Single Target** or **Multi Target** → **Start** → use **+** on the float bar to place targets → **Play**.

## Project structure

- `app/src/main/java/net/mtautoclicker/android/ui/` — Jetpack Compose dashboard
- `service/MtAccessibilityService.kt` — gesture-based clicks
- `service/FloatingOverlayService.kt` — float bar + target picker
- `service/ClickAutomationService.kt` — click loop engine
- `data/` — presets, settings, tracking

## Store submission

See `store/PUBLISHING_GUIDE.md` for Google Play listing steps.

## Related products

| Platform | Product | Store |
|----------|---------|-------|
| Chrome browser | `mt-autoclicker-extension/` | [Chrome Web Store](https://chromewebstore.google.com/detail/mt-auto-clicker/cbnaambdpgibknnmdnjeangmaeicmjoj) |
| Windows | MT Auto Clicker desktop | mtautoclicker.net |
| Chrome OS / Android | This app | Google Play |

## Privacy

Anonymous device ID and usage events may be sent to `https://mtautoclicker.net/api/tracking/batch/`. Toggle in Settings.
