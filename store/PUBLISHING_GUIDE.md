# Auto Clicker - MT — Google Play Publishing Guide (Android v1.0.0)

Guide for publishing the Android app (`mt-autoclicker-android/`) to Google Play so Chromebook and Android users can install it.

---

## Item overview

| Field | Value |
|-------|--------|
| **Play Store title** | Auto Clicker - MT |
| **Installed app name** | MT Auto Clicker |
| **Package** | `net.mtautoclicker.android` |
| **Version** | `1.0.0` (versionCode 1) |
| **Category** | Tools or Productivity |
| **Form factors** | Phone, Tablet, **Chromebook** |
| **Content rating** | Everyone (adjust after questionnaire) |

---

## Before you upload

> **Metadata warning:** Do not submit the reserved title `Auto Clicker No Ads`.
> Google Play explicitly prohibits promotional text such as “Free” and “No Ads”
> in app titles. `Auto Clicker - MT` retains the searched keyword without the
> policy violation. In Play Console, answer **Contains ads: No**.

1. **Create an upload keystore** (keep two offline backups):

```bash
keytool -genkeypair -v -keystore mt-upload.jks -alias mt_upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

2. Keep secrets outside Git and provide them as environment variables:

```bash
export MT_UPLOAD_STORE_FILE="/absolute/path/to/mt-upload.jks"
export MT_UPLOAD_STORE_PASSWORD="..."
export MT_UPLOAD_KEY_ALIAS="mt_upload"
export MT_UPLOAD_KEY_PASSWORD="..."
```

The Gradle release configuration reads these values without storing passwords
in the repository. Enrol in **Play App Signing** when creating the first release.

3. Build release AAB (required for Play):

```bash
cd mt-autoclicker-android
./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

---

## Play Console checklist

### App content
- [ ] Privacy policy URL: `https://mtautoclicker.net/privacy-policy/` — deploy the updated page before submission
- [ ] Data safety form: follow `store/DATA_SAFETY.md`
- [ ] **Accessibility API declaration** — explain deterministic user-placed click automation (not an AI agent)
- [ ] Accessibility declaration video — follow `store/ACCESSIBILITY_DECLARATION.md`
- [ ] **Overlay permission** — explain floating control bar for automation
- [ ] Foreground service type declarations for `specialUse` and `mediaProjection`
- [ ] Ads declaration: **No, this app does not contain ads**
- [ ] App access: no login required
- [ ] Target audience & content rating questionnaire

### Store listing
- [ ] Copy listing text from `store/listing/en-US/`
- [ ] Confirm title is `Auto Clicker - MT`, not `Auto Clicker No Ads`
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
2. Set the default store title to **Auto Clicker - MT**
3. Enrol in Play App Signing and upload the signed `app-release.aab`
4. Start with **Internal testing**
5. If this is a personal account created after November 13, 2023, run a closed
   test with at least 12 continuously opted-in testers for 14 days
6. Test phone, tablet and Chromebook permission/automation flows
7. Complete all policy declarations and upload the Accessibility demo video
8. Promote to **Production**

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
