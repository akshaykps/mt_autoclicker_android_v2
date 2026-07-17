# Build, install & launch — MT Auto Clicker Android

Quick reference for developing on a Mac with `adb`.

Package: `net.mtautoclicker.android`  
Main activity: `net.mtautoclicker.android/.MainActivity`  
Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## 0. One-time setup

```bash
# From the android project root
cd mt-autoclicker-android

# Point adb / SDK tools (Apple Silicon Mac default path)
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

If you see `zsh: command not found: adb`, either run the `export PATH=...` line above in that terminal, or add this to `~/.zshrc` once:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools"
```

Then reload:

```bash
source ~/.zshrc
which adb
```

Or call adb by full path:

```bash
~/Library/Android/sdk/platform-tools/adb devices -l
```

On the phone:

1. **Settings → About phone** → tap Build number 7 times (Developer options).
2. Enable **USB debugging**.
3. For wireless: enable **Wireless debugging**.

---

## 1. Build the app

### Debug APK (daily use)

```bash
cd mt-autoclicker-android
./gradlew assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Release APK

```bash
./gradlew assembleRelease
```

Output:

```text
app/build/outputs/apk/release/app-release.apk
```

### Clean rebuild

```bash
./gradlew clean assembleDebug
```

---

## 2. Scan connected devices

```bash
adb devices -l
```

Example:

```text
List of devices attached
ff345386               device usb:... model:CPH2619
192.168.0.104:42931    device product:RMX3785IN model:RMX3785
```

- `device` = ready  
- `unauthorized` = unlock phone and tap **Allow USB debugging**  
- `offline` = reconnect / toggle debugging  

Restart adb if nothing shows:

```bash
adb kill-server
adb start-server
adb devices -l
```

### Wireless debugging (pair once)

On phone: **Wireless debugging → Pair device with pairing code**  
Note the **IP:pairing-port** and **6-digit code**.

```bash
adb pair 192.168.0.106:38997 571956
```

Then connect using the **IP:port** shown on the main Wireless debugging screen (not the pairing port):

```bash
adb connect 192.168.0.106:42931
adb devices -l
```

Auto-discover connect endpoint (when mDNS works):

```bash
adb mdns services
# Look for:  _adb-tls-connect._tcp   →   IP:PORT
adb connect IP:PORT
```

Disconnect wireless:

```bash
adb disconnect
# or
adb disconnect 192.168.0.106:42931
```

---

## 3. Install the app

USB or already-connected wireless device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` = replace existing install (keeps app data).

Specific device when several are connected:

```bash
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

Example:

```bash
adb -s 192.168.0.104:42931 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s ff345386 install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 4. Launch the app

```bash
adb shell am start -n net.mtautoclicker.android/.MainActivity
```

Force-stop then launch (fresh process):

```bash
adb shell am force-stop net.mtautoclicker.android
adb shell am start -n net.mtautoclicker.android/.MainActivity
```

On a specific device:

```bash
adb -s DEVICE_SERIAL shell am start -n net.mtautoclicker.android/.MainActivity
```

---

## 5. Build + install + launch (one shot)

### Single device (USB or wireless already connected)

```bash
cd mt-autoclicker-android
./gradlew assembleDebug && \
  adb install -r app/build/outputs/apk/debug/app-debug.apk && \
  adb shell am force-stop net.mtautoclicker.android && \
  adb shell am start -n net.mtautoclicker.android/.MainActivity
```

### Pick first online device automatically

```bash
cd mt-autoclicker-android
./gradlew assembleDebug

DEV=$(adb devices | awk '/\tdevice$/{print $1; exit}')
echo "Using: $DEV"

adb -s "$DEV" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$DEV" shell am force-stop net.mtautoclicker.android
adb -s "$DEV" shell am start -n net.mtautoclicker.android/.MainActivity
```

### Reconnect wireless via mDNS, then install + launch

```bash
cd mt-autoclicker-android
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"

EP=$(adb mdns services 2>/dev/null | awk '/_adb-tls-connect/{print $3; exit}')
echo "Connect endpoint: $EP"
adb connect "$EP"

./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop net.mtautoclicker.android
adb shell am start -n net.mtautoclicker.android/.MainActivity
```

---

## 6. Useful extras

### Uninstall

```bash
adb uninstall net.mtautoclicker.android
```

### App info / model

```bash
adb shell getprop ro.product.model
adb shell getprop ro.product.manufacturer
adb shell dumpsys package net.mtautoclicker.android | head -40
```

### Live logcat (filter app)

```bash
adb logcat --pid="$(adb shell pidof -s net.mtautoclicker.android)"
# or
adb logcat | grep -i mtautoclicker
```

### Screenshot from device

```bash
adb exec-out screencap -p > screen.png
```

---

## Troubleshooting

| Symptom | What to try |
|--------|-------------|
| `adb: no devices/emulators found` | Cable (data, not charge-only), USB debugging on, Allow prompt, or wireless pair again |
| `unauthorized` | Unlock phone → Allow USB debugging; or Revoke authorizations and replug |
| `offline` / `Connection refused` | Toggle Wireless debugging off/on; `adb disconnect` then `adb connect IP:PORT` again |
| Install fails (signatures) | `adb uninstall net.mtautoclicker.android` then install again |
| Overlay / Accessibility missing | Open app → Settings → grant Overlay + Accessibility |

---

## Quick cheat sheet

```bash
# PATH
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"

# Build
./gradlew assembleDebug

# Devices
adb devices -l

# Install + launch
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n net.mtautoclicker.android/.MainActivity
```
