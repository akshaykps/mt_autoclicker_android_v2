# MT Auto Clicker — Google Play Publishing Guide (Chrome OS / Android v1.0.0)

Guide for publishing the Android app (`mt-autoclicker-android/`) to Google Play so Chromebook and Android users can install it.

---

## Item overview

| Field | Value |
|-------|--------|
| **Title** | MT Auto Clicker |
| **Package** | `net.mtautoclicker.android` |
| **Version** | `1.0.0` (versionCode 1) |
| **Category** | Tools or Productivity |
| **Form factors** | Phone, Tablet, **Chromebook** |
| **Content rating** | Everyone (adjust after questionnaire) |

---

## Before you upload

1. **Create a release keystore** (keep offline backup):

```bash
keytool -genkey -v -keystore mt-release.keystore -alias mt_autoclicker \
  -keyalg RSA -keysize 2048 -validity 10000
```

2. Add signing to `app/build.gradle.kts` (or use Android Studio **Generate Signed Bundle**).

3. Build release AAB (required for Play):

```bash
cd mt-autoclicker-android
./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

---

## Play Console checklist

### App content
- [ ] Privacy policy URL: `https://mtautoclicker.net/privacy/` (or your live policy page)
- [ ] Data safety form: declare device ID + usage analytics to `mtautoclicker.net`
- [ ] **Accessibility API declaration** — explain deterministic user-placed click automation (not an AI agent)
- [ ] **Overlay permission** — explain floating control bar for automation
- [ ] Target audience & content rating questionnaire

### Store listing
- [ ] Short description (80 chars): `Free auto clicker for Chromebook & Android — single & multi target tapping.`
- [ ] Full description — highlight Chrome OS, float bar, presets, kill switch
- [ ] App icon 512×512 (use `store/icon-512.png` or extension assets)
- [ ] Feature graphic 1024×500
- [ ] **Chromebook screenshots** (windowed app, mouse targeting) — required for Chrome OS visibility
- [ ] Phone screenshots (optional but recommended)

### Chrome OS
- [ ] In Play Console → **Device catalog** → ensure app is compatible with Chromebooks
- [ ] Test on a real Chromebook: overlay + accessibility + float bar over Android apps
- [ ] Declare large-screen support; avoid phone-only layouts

### Permissions review tips
Google Play scrutinizes Accessibility automation apps. Your listing should:
- State clearly that users **choose click positions**
- Not claim to be a disability-only tool unless it genuinely is
- Include in-app disclosure before enabling Accessibility
- Avoid language about “automatically doing tasks for you” or AI agents

---

## Upload steps

1. [Google Play Console](https://play.google.com/console) → Create app
2. **Internal testing** track → Upload `app-release.aab`
3. Add testers → verify on Chromebook + phone
4. Complete all policy declarations
5. Promote to **Production**

---

## Post-launch

- Add Play Store link on `https://mtautoclicker.net` (“Download for Chromebook”)
- Track installs via existing backend (`os_platform`: `chromeos` / `android`)
- Monitor reviews mentioning “doesn’t work on Chromebook” — usually missing Accessibility or overlay permission

---

## Related

| Product | Path | Store |
|---------|------|-------|
| Browser extension | `../mt-autoclicker-extension/` | Chrome Web Store (live) |
| This app | `mt-autoclicker-android/` | Google Play |
| Backend | `../mt_autoclicker_net-main 3/` | mtautoclicker.net |
