# MT Auto Clicker for Android — Complete User Guide

This guide explains every main screen and user-facing feature in MT Auto Clicker for Android and ChromeOS. It is written for regular users, so you do not need technical knowledge.

> **Important:** Only automate apps and actions that you are allowed to automate. Do not use automation to break an app's rules, create unwanted traffic, bypass security, or perform unsafe actions. Always test with a short run first.

## Contents

1. [What MT Auto Clicker Does](#1-what-mt-auto-clicker-does)
2. [Requirements](#2-requirements)
3. [First-Time Setup and Permissions](#3-first-time-setup-and-permissions)
4. [Home Screen](#4-home-screen)
5. [Common Options Used by Multiple Features](#5-common-options-used-by-multiple-features)
6. [Single Target](#6-single-target)
7. [Multi Target](#7-multi-target)
8. [Macro Recorder and Playback](#8-macro-recorder-and-playback)
9. [Full Page Screenshot](#9-full-page-screenshot)
10. [Auto Refresh](#10-auto-refresh)
11. [Floatbars and Their Controls](#11-floatbars-and-their-controls)
12. [Recent Runs and Presets](#12-recent-runs-and-presets)
13. [Notifications and Feedback Replies](#13-notifications-and-feedback-replies)
14. [Feedback Screen](#14-feedback-screen)
15. [Settings Screen](#15-settings-screen)
16. [Backup, Import, and Data Removal](#16-backup-import-and-data-removal)
17. [Tours, Review Prompts, and Helpful Messages](#17-tours-review-prompts-and-helpful-messages)
18. [Real-Life Examples](#18-real-life-examples)
19. [Troubleshooting](#19-troubleshooting)
20. [Privacy and Safety](#20-privacy-and-safety)
21. [Quick Reference](#21-quick-reference)

---

## 1. What MT Auto Clicker Does

MT Auto Clicker can perform repeated taps and gestures on Android phones, tablets, and Chromebooks that support Android apps.

The app contains five main tools:

- **Single Target:** Repeatedly tap one point or one selected area.
- **Multi Target:** Dispatch every numbered target in order once per cycle. In the current version, the Parallel switch is saved but does not change runtime behavior.
- **Macro Recorder:** Record taps, holds, swipes, text actions, and supported system navigation, then play them back.
- **Full Page Screenshot:** Capture a long chat, feed, list, or webpage by automatically scrolling and stitching screenshots together.
- **Auto Refresh:** Perform a pull-to-refresh gesture in another app at a chosen interval.

It also includes:

- Recent-run history
- Saved presets
- Import and export backup
- Feedback and support replies
- In-app update, event, and announcement notifications
- Light, dark, and system themes

---

## 2. Requirements

### Supported system

- Android 8.0 or newer
- Android phone or tablet
- Chromebook with Google Play / Android app support

Full Page Screenshot saving is most reliable on Android 10 or newer. On Android 8–9, saving to Pictures may fail because the current build does not request the older storage-write permission.

### Required permissions

Most automation tools require:

1. **Display over other apps**
2. **MT Auto Clicker Accessibility service**

Full Page Screenshot also asks for Android's **screen capture / media projection** permission when a capture session begins.

Notifications may ask for permission on Android 13 or newer. Notification permission is useful for recording controls, saved-capture messages, automation status, and app announcements.

---

## 3. First-Time Setup and Permissions

When you first open the app, the onboarding tour introduces the main tools and permission requirements.

### Permission 1: Display over other apps

**What it is for**

- Shows the MT floatbar over another app.
- Shows target markers for Single Target and Multi Target.
- Shows Screenshot, Refresh, and Macro playback controls.

**How to enable it**

1. Open **Settings** in MT Auto Clicker.
2. Open **Permissions** or tap **Overlay**.
3. Android opens the “Display over other apps” page.
4. Enable permission for **MT Auto Clicker**.
5. Return to MT Auto Clicker.

The permission screen updates automatically when you return.

### Permission 2: Accessibility service

**What it is for**

- Performs taps at the selected positions.
- Performs swipes and pull-to-refresh gestures.
- Records and plays supported macro actions.
- Supports Back, Home, Recents, and notification-shade actions during macro use where the device allows them.

**How to enable it**

1. Open **Settings → Permissions → Accessibility**.
2. Find **MT Auto Clicker** in Android Accessibility settings.
3. Open it and enable the service.
4. Read Android's confirmation message, then confirm.
5. Return to MT Auto Clicker.

> After updating or reinstalling the app, Android may require you to turn the Accessibility service off and on again.

### Permission screen status

The Permissions screen displays:

- **0 of 2 enabled:** Both permissions are missing.
- **1 of 2 enabled:** One permission still needs attention.
- **2 of 2 enabled / You're all set:** Automation is ready.

Buttons shown on the permission cards:

- **Enable overlay / Manage**
- **Open Accessibility / Manage**
- **Continue to Home** when both permissions are enabled

### Screenshot capture permission

Full Page Screenshot uses Android's system capture dialog.

1. Start Full Page Screenshot.
2. When Android asks what to share, choose **Entire screen**.
3. Confirm **Start now** or the equivalent button on your device.

Choose **Entire screen** so MT Auto Clicker can capture whichever screen is in front. Selecting an app in Setup only launches it; capture is not locked to that app.

---

## 4. Home Screen

The Home screen is the main dashboard.

### Header

The header contains:

- MT Auto Clicker logo and name
- “Click less. Do more.” subtitle
- Notification bell
- Red notification dot when unread messages are available

Tap the bell to open the Notifications screen.

### Statistics

The three small cards display activity from the current app session:

- **Clicks:** Number of automated clicks performed
- **Runtime:** Total automation running time
- **Runs:** Number of automation runs

These process-session statistics cover successful Single Target and Multi Target runs only. Macro playback, screenshots, and Auto Refresh are not included. Clicks count successful gesture dispatches, which does not guarantee that the target app accepted every action.

### Quick start strip

The compact Quick Start strip explains the usual workflow:

1. **Pick** — choose a feature.
2. **Start** — configure it and press Start.
3. **Place** — place targets when the feature uses targets.
4. **Play** — begin from the floatbar.

Tap a Quick Start step to see its short hint.

### Automation cards

Tap one of the five cards:

- Single Target
- Multi Target
- Macro Recorder
- Full Page Screenshot
- Auto Refresh

### Floating navigation dock

The bottom dock contains:

- **Home**
- **Presets**
- **Feedback**
- **Inbox**
- **Settings**

The active screen is highlighted.

The dock shows labels near the top. Scrolling downward changes it into a smaller icon-only pill; scrolling upward expands it again, even before reaching the top.

---

## 5. Common Options Used by Multiple Features

### Setup and Recent tabs

Single Target, Multi Target, Full Page Screenshot, and Auto Refresh have two tabs:

- **Setup:** Configure and start a new run.
- **Recent:** View recently used configurations.

Recent items can usually be:

- **Loaded** into Setup
- **Run** immediately
- **Saved** permanently to Presets
- **Deleted**

The app keeps up to 10 recent configurations per feature. Running the same configuration again replaces its older duplicate.

### Run summary

The summary pill near the top shows important settings, such as:

- Interval
- Target or click mode
- Stop condition
- Selected app

On Single Target, Multi Target, and Auto Refresh, tapping the summary/edit action jumps to the settings area.

### Interval

The interval is the wait between actions.

Available units:

- **ms:** milliseconds
- **s:** seconds
- **min:** minutes

Single/Multi Target accept a minimum configured interval of **10 ms**, or up to about 100 scheduled cycles per second. Accessibility dispatch and device load can make the achieved rate much slower. Auto Refresh has a separate effective minimum of **500 ms**.

Examples:

- `100 ms` = 10 scheduled cycles per second
- `500 ms` = 2 actions per second
- `1 s` = 1 cycle per second
- `5 s` = one cycle every 5 seconds

For Multi Target, the interval is between complete cycles. Targets inside a cycle are dispatched back-to-back. For example, `1 s` with three targets means three rapid taps followed by approximately a one-second wait.

### Variable interval

When **Variable interval** is on, the delay changes between the entered **Min** and **Max** values.

Use it when you do not want every action to happen at exactly the same timing.

Example:

- Unit: ms
- Min: 600
- Max: 1000

Each action waits approximately 0.6 to 1 second.

Set Min lower than or equal to Max. The screen does not report reversed values; if Max is below Min, runtime effectively uses Min instead of a range.

### Start delay

**Start delay (ms)** waits before the first automated action.

Examples:

- `0` = begin immediately
- `2000` = wait 2 seconds
- `5000` = wait 5 seconds

This is useful when you need time to open a menu before tapping begins.

Duration timing begins before the Start delay. A long delay therefore uses part of the configured duration. Stop conditions are checked after an action attempt, so a zero-second duration can still perform one attempt.

### Random offset

**Random offset (px)** slightly changes the click position around the target.

- `0` = always use the exact selected position
- A small value such as `2–5` = small position variation
- Large values may tap outside the intended button

Use this carefully on small controls.

Random offset affects Single/Multi Target clicks only. Auto Refresh displays the shared field, but its pull gesture currently ignores Random offset.

### Stop condition

Three stop types are available:

#### Never

Runs until you manually stop it.

#### Cycles

Stops after the chosen number of cycles.

- Single Target: one cycle is one click.
- Multi Target sequence: one cycle completes the full target list.
- Multi Target parallel: one cycle triggers each target once.
- Auto Refresh: one cycle is one refresh gesture.

#### Duration

Stops after the chosen number of seconds.

Default Single/Multi behavior is a 10-second duration. Default Auto Refresh behavior is a 60-second duration.

### Number fields and keyboard

Tap a number field to edit it. To close the keyboard:

- Tap the check/done icon in the field.
- Use the keyboard's Done button if available.
- Tap outside the field.
- Scroll the page.

These extra methods help on phones whose numeric keyboard does not show a hide button.

---

## 6. Single Target

Single Target repeatedly taps one point or an area.

### When to use it

- Repeatedly press a “Collect” button
- Keep a game button active
- Refresh one web control
- Advance a slideshow
- Tap one stable position for testing

### Setup options

#### Target type: Point

Point mode taps one exact screen position.

How to use:

1. Choose **point**.
2. Configure timing and stop condition.
3. Press **Start**.
4. Open the app you want to control.
5. Tap **+** on the floatbar.
6. Tap the desired position.
7. Press **Play**.

#### Target type: Zone

Zone mode uses an area instead of one exact point.

How to use:

1. Choose **zone**.
2. Press **Start**.
3. Tap **+** on the floatbar.
4. Drag across the screen to draw the click zone.
5. Press **Play**.

The automation chooses positions inside the zone. Zone mode is helpful when the intended control moves slightly.

### Timing options

Single Target supports:

- Interval and unit
- Variable interval with Min and Max
- Start delay
- Random offset
- Never / Cycles / Duration stop condition

### Single Target floatbar

In the ready state:

- **Drag handle:** Move the floatbar.
- **Play:** Start clicking. It is disabled until a target exists.
- **Save:** Quickly save the current setup as a preset.
- **Eye:** Show or hide target markers.
- **MT logo:** Return to Single Target and dismiss the floatbar.
- **+ / Remove:** Add the target, or remove the existing Single Target.
- **Settings:** Edit interval, stop condition, target type, delay, and random offset without returning to the main app.
- **Close:** End the session and remove overlays.

Saving the floatbar Settings panel rebuilds the interval and turns Variable interval off. Restore Min/Max from the main Setup screen when needed. If you change Point/Zone in this panel, remove and re-place the target so its geometry matches the new mode.

While running:

- **Status dot:** Shows running or paused state.
- **Pause / Play:** Request pause or resume. If the loop reaches its next cycle boundary while paused, this version can finish the run and return to Ready instead of remaining paused.
- **MT logo:** Return to Single Target and end the floatbar session.
- **Stop:** Stop the active run.

### Example: Repeatedly tap a reward button

1. Open Single Target.
2. Select **point**.
3. Set interval to `1 s`.
4. Set stop type to **Cycles** and cycles to `30`.
5. Press Start.
6. In the other app, tap **+** and then tap the reward button.
7. Press Play.

The button is tapped once per second for 30 cycles.

---

## 7. Multi Target

Multi Target places numbered targets and taps all of them.

### When to use it

- Complete a repeated multi-step form
- Tap several buttons in a fixed order
- Cycle through game controls
- Test multiple screen positions
- Trigger several independent points each cycle

### Sequence and Parallel modes

The current Android runtime always dispatches targets in numbered order, back-to-back:

`1 → 2 → 3 → wait for interval → 1 → 2 → 3 ...`

The **Parallel (all targets each cycle)** switch is saved in presets and shown in the summary, but the current click service does not use it. Turning it on currently produces the same result as Sequence mode.

### How to place targets

1. Configure timing, stop condition, and click mode.
2. Press **Start**.
3. Tap **+**.
4. Tap the first position.
5. Repeat **+ → position** for every target.
6. Check the numbered markers.
7. Press **Play**.

### Multi Target floatbar

Ready-state controls:

- **Drag handle**
- **Play**
- **Save preset**
- **Show/hide markers**
- **MT logo:** Return to Multi Target and dismiss the floatbar
- **+**: Add another numbered target
- **−**: Remove the last target
- **Settings**
- **Close**

Running-state controls:

- Pause / resume, with the same cycle-boundary limitation as Single Target
- MT logo to return and dismiss
- Stop

### Example: Tap three buttons in order

1. Set interval to `750 ms`.
2. Keep Parallel off. Parallel currently has the same runtime behavior.
3. Set stop type to **Cycles**, value `10`.
4. Place target 1 on the first button.
5. Place target 2 on the second button.
6. Place target 3 on the third button.
7. Press Play.

The app completes the three-button sequence 10 times.

---

## 8. Macro Recorder and Playback

Macro Recorder captures a sequence of actions and saves it on the device.

### Record tab

Tap **Start Record**, then confirm the start popup.

The recorder can capture:

- Taps
- Long presses
- Swipes
- Drag paths
- Delays between actions
- Text placed into supported focused editable fields
- Back
- Home
- Recents
- Notification shade gesture

The recording notification contains important controls. Depending on the device, use the notification actions for Back, Home, Recents, or Stop when system gestures are reserved by Android.

Back can execute while recording. Home, Recents, and Notifications are recorded but deliberately not executed live; they run during playback. Notification actions provide an alternative way to add these steps.

A long press is stored as a hold for playback, but its live reinjection during recording is currently a short tap. Do not depend on the target app reacting to the hold while you record it.

### How to record

1. Open **Macro Recorder → Record**.
2. Tap **Start Record**.
3. Confirm the recording popup.
4. Perform the actions to record. Touches are intercepted and reinjected through Accessibility rather than passed through normally.
5. Use the recording notification's **Stop** action to finish.
6. If the notification shade cannot be opened during recording, use an OEM notification shortcut or notification-history/control surface. The recording overlay has no separate floating Stop button.
7. The recording is saved on this device.
8. Open the Playback tab or use the notification action to play it.

### Recent recordings

The Record tab shows the three newest macros.

Each macro row shows:

- Name
- Duration
- Action count
- Age, such as “today” or “3 days ago”

Actions:

- **Play icon:** Open playback controls.
- **Bookmark:** Save the macro as a preset.
- **Delete:** Permanently remove the recording.

Tap **View all** or **Open playback library** to open the full library.

### Playback tab

The Playback tab lists all recorded macros stored on the device.

Tap Play on a macro to open the playback floatbar.

### Playback floatbar

Controls:

- **Drag handle:** Move the floatbar.
- **Action count:** Number of recorded steps.
- **MT logo:** Return to Macro Recorder and dismiss the playback floatbar.
- **0.5x:** Half-speed playback.
- **1x:** Original-speed playback.
- **2x:** Double-speed playback.
- **Play:** Start playback.
- **Close:** End the macro session.

The floatbar is hidden once playback starts. Pause, Resume, Stop, and action progress are then available from the ongoing playback notification. Merely opening MT Auto Clicker does not stop active playback.

### Playback speed

- **0.5x:** Adds more time between actions; useful when an app loads slowly.
- **1x:** Uses recorded timing.
- **2x:** Runs faster; some apps may not keep up.

### Loop behavior

Ordinary recordings and newly created macro presets play once. The current UI has no Loop or loop-count control. The playback engine can honor loop values only when they already exist in an imported or externally created configuration. Stop an active loop from the playback notification.

### Recorded text

Text playback uses Accessibility's Set Text action. It replaces the focused editable field's contents with the recorded full text; it does not replay individual keyboard keystrokes.

### What may not record reliably

- Multi-finger gestures and pinch-to-zoom
- Some secure system dialogs
- Some keyboard typing, especially secure/password fields
- Gestures Android reserves at screen edges
- Actions inside apps that reject Accessibility-generated input

### Important macro notes

- Keep Accessibility enabled for recording and playback.
- Some apps intentionally ignore automated gestures.
- Macro data is stored on the device.
- Test a macro at 0.5x before using faster playback.
- Never record passwords, payment details, or other sensitive information.

### Example: Open a menu and select an item

1. Start recording.
2. Tap the menu button.
3. Wait for the menu to open.
4. Tap the item.
5. Stop recording.
6. Play at 1x.

The recorded wait between steps helps the menu appear before the second tap.

---

## 9. Full Page Screenshot

Full Page Screenshot captures multiple frames while scrolling and stitches them into one tall image.

### Good uses

- Long WhatsApp or messaging conversations
- Webpages
- Social media feeds
- Settings pages
- Long lists and receipts

### Choose app

The Setup tab lists launchable apps.

Tools:

- **Search:** Search by app name or package name.
- **All:** Show all launchable apps.
- **Popular:** Show commonly used apps.
- **Browsers:** Show browser apps.
- **Remember my choice:** Automatically select the same app next time.

### How to capture

1. Select an app.
2. Tap **Start**.
3. Choose **Entire screen** when Android asks for screen capture permission.
4. The selected app is launched once. You may switch elsewhere before Snapshot; the foreground screen is what gets captured.
5. Navigate to the top of the long page, chat, feed, or list.
6. Tap the Screenshot button on the floatbar.
7. Do not touch or scroll while capture is running.
8. Wait for the saved message.

### Screenshot floatbar

- **Drag handle:** Move the floatbar.
- **Shot / status text:** Shows the current state.
- **MT logo:** Return to Full Page Screenshot, stop the session, and dismiss the floatbar.
- **Screenshot icon:** Begin capture and stitching.
- **Close:** End the capture session.

During capture, the app scrolls in steps, captures overlapping frames, removes repeated areas where possible, and stitches the result.

Once capture starts, the main floatbar is replaced by a non-interactive Capturing/Saving status pill. There is no on-screen Cancel control until capture finishes or fails.

A capture is limited to 36 frame attempts. Output taller than 22,000 pixels is scaled down, including its width. “Full Page” is therefore a bounded scroll-and-stitch capture, not a guarantee that an unlimited page is kept at native resolution.

### Where screenshots are saved

Saved images appear in:

**Gallery → Pictures → MT Auto Clicker**

A saved notification may also open the image.

### Best results

- Begin at the top of the content.
- Use a screen with one clear vertical scroll area.
- Do not touch the screen during “Scrolling & stitching.”
- Wait for images and messages to finish loading before capture.
- Close keyboards, popups, and menus first.
- Avoid pages with videos or constantly changing animations.
- Pages with changing toolbars, nested scrollers, large sticky areas, or content that changes between frames may have seams or missing content.

### Example: Capture a long chat

1. Select the messaging app.
2. Start and allow Entire screen.
3. Open the conversation.
4. Scroll to the beginning of the section you want.
5. Tap Screenshot.
6. Wait for “Saved to Gallery.”

---

## 10. Auto Refresh

Auto Refresh performs a downward pull gesture at a chosen interval.

### Good uses

- Refresh a browser page
- Update a social feed
- Refresh a game lobby
- Watch a status page
- Reload a list that supports pull-to-refresh

### Default configuration

- Interval: 5 seconds
- Stop condition: Duration
- Duration: 60 seconds

The first pull starts immediately after tapping Refresh, or after Start delay. The interval is the delay after each gesture and is clamped to at least 500 ms; each pull gesture lasts about 480 ms.

### Choose app

Use the app grid and search box to choose the target app.

**Remember my choice** stores the selected app for the next Auto Refresh setup.

App selection only launches the app. Refresh gestures target whichever screen is currently in front, so switching apps after Start redirects refreshes to that screen.

### How to run

1. Choose the interval and unit.
2. Choose the stop condition.
3. Select an app.
4. Tap **Start**.
5. The selected app opens.
6. Tap the Refresh button on the floatbar.

### Auto Refresh floatbar

- **Drag handle**
- **Status:** Ready, running count, paused, or refresh status
- **MT logo:** Return to Auto Refresh, stop the session, and dismiss the floatbar
- **Refresh / Pause / Play:** Start, pause, or resume
- **Close:** End the refresh session

### Important limitation

Auto Refresh performs a pull-down gesture. It works only where the target app recognizes pull-to-refresh. A desktop-style browser refresh button is not pressed automatically.

A Cycles count increases only when Accessibility reports a successful gesture. If dispatch repeatedly fails, a Cycles-only run may not finish; use Duration or stop it from the floatbar/notification.

### Example: Refresh a status page every 15 seconds

1. Set interval to `15 s`.
2. Set stop type to **Duration**.
3. Enter `600` seconds for 10 minutes.
4. Select the browser.
5. Start.
6. Open the desired tab.
7. Tap Refresh on the floatbar.

---

## 11. Floatbars and Their Controls

Floatbars appear over other apps. Their exact controls depend on the feature.

### Common behavior

- Use the dotted handle to drag a floatbar.
- The floatbar remains visible over other apps until stopped or dismissed.
- Tap the **MT logo** to return directly to that feature's main screen.
- Returning with the MT logo also dismisses that feature's floatbar/session.
- Tap **Close** to end the current session without opening MT Auto Clicker.

### Common colors

- Blue: Single Target or general action
- Purple: Multi Target or Macro
- Cyan: Screenshot
- Amber: Auto Refresh
- Green: Start or Play
- Orange: Pause/running control
- Red: Stop or destructive action
- Gray: Close/cancel

### Target markers

- Single Target uses one marker or zone.
- Multi Target uses numbered markers.
- The Eye button hides or shows markers without removing their saved positions.
- Marker size can be changed in **Settings → Appearance → Marker**.

### Floatbar settings panel

Single/Multi Target include a Settings button that opens an overlay panel.

It can edit:

- Stop condition: Never, Cycles, Duration
- Cycle count
- Duration in seconds
- Click interval
- Unit: ms, sec, min
- Target type for Single Target
- Parallel clicks for Multi Target
- Start delay
- Random offset

Tap **Save** to apply changes or **Cancel** to close without applying.

Saving this panel turns Variable interval off because it rebuilds only the basic interval values. Configure Min/Max again from the main Setup screen. After changing Point/Zone, remove and re-place the target.

For variable Min/Max interval configuration, use the feature's main Setup screen.

---

## 12. Recent Runs and Presets

### Recent runs

Recent runs are temporary history entries created automatically:

- Single/Multi when a run finishes or is manually stopped
- Full Page Screenshot when a capture setup starts
- Auto Refresh when a setup starts

The app keeps up to 10 recent items per feature.

### Save a recent run

1. Open the feature.
2. Open **Recent**.
3. Find the run.
4. Tap **Save**.
5. Enter a name or accept the generated name.

Generated name prefixes:

- `STAC-1` — Single Target
- `MTAC-1` — Multi Target
- `MAC-1` — Macro
- `FPS-1` — Full Page Screenshot
- `AR-1` — Auto Refresh

### Presets screen

The Presets screen displays permanently saved presets.

Filters:

- All
- Single
- Multi
- Macro
- Screenshot
- Refresh

Each preset card shows:

- Preset name
- Feature type
- Number of targets, when relevant
- Creation date

Actions:

- **Load & start:** Open the correct feature session using saved settings.
- **Delete:** Permanently delete the preset.

### Presets versus macros

A recorded macro and a macro preset are related but different:

- **Recorded macro:** The original recording in the Macro library.
- **Macro preset:** A saved playback configuration that contains the macro steps.

Deleting all presets does not delete the Macro library. Factory Reset deletes both.

---

## 13. Notifications and Feedback Replies

Open the Inbox from:

- Home header bell
- Bottom navigation dock

### Notification types

- **App Update**
- **What's New**
- **Upcoming**
- **Event**
- **Feedback Reply**
- **Notice**

### Inbox controls

- **Refresh:** Download the newest messages.
- **Mark all read:** Mark every visible unread message as read.
- **All:** Show all messages.
- **Unread:** Show only unread messages.

Tap a card to mark it read.

Some notifications contain:

- An **Open** button
- A web link
- A link to another screen in MT Auto Clicker

### Feedback replies

When you submit feedback, MT Auto Clicker sends the app's Device ID with the message. If the support team replies from the Android dashboard, the reply appears as **Feedback Reply** in your Inbox.

The Inbox refreshes when opened. Use the Refresh button if a reply does not appear immediately.

> The current Inbox is an in-app notification center. It is not guaranteed to deliver an instant push while the app is fully closed.

---

## 14. Feedback Screen

The Feedback screen sends feedback directly to the MT Auto Clicker Android dashboard.

### Feedback type

#### Feature

Use for comments about an existing feature. Select one or more related features.

#### Suggestion

Choose:

- **New feature:** A brand-new idea
- **Existing:** An improvement to an existing feature

When Existing is selected, choose the related feature.

#### Bug

Choose:

- **Feature bug:** A problem tied to a feature
- **Something else:** An app-wide or other problem

When Feature bug is selected, choose the affected feature.

#### Other

Use for general questions or notes.

### Selectable feature areas

- Single Target
- Multi Target
- Macro Recorder
- Full Page Screenshot
- Auto Refresh
- Presets
- Float bar & overlays
- Settings & Permissions

### Message rules

- Minimum: 10 characters
- Maximum: 500 characters

For bugs, include:

1. What you were trying to do
2. What happened
3. What you expected
4. Your phone brand/model if the problem seems device-specific
5. Steps that reproduce the issue

Do not include passwords, payment information, private messages, or other sensitive data.

### Example bug report

> On a Realme phone, I opened Single Target and edited the interval. The numeric keyboard had no hide key. I expected a Done button. This happens every time on Android 14.

---

## 15. Settings Screen

### Community Support

Opens the MT Auto Clicker community:

`https://community.mtautoclicker.net`

### Device

#### Device ID

A randomly generated identifier for this app installation.

It is used for:

- Associating feedback with a reply
- Delivering device-specific Inbox messages
- Anonymous app event records

Tap the copy icon to copy it. Support may ask for this ID.

The Device ID is not included in backup exports. Factory Reset generates a new ID.

### Appearance

#### Theme

- **System:** Follow Android light/dark mode.
- **Light:** Always use light theme.
- **Dark:** Always use dark theme.

#### Marker

Changes the size of Single/Multi Target markers.

- Minimum: 50%
- Maximum: 200%
- Change step: 10%
- Default: 100%

Increase it when markers are hard to see. Decrease it when markers cover small controls.

### Preferences

#### Notification sound

This switch is stored and included in backups, but the current notification code does not read it. Changing it may have no visible effect in this version. Use Android's notification-channel controls instead:

**Settings → Apps → MT Auto Clicker → Notifications**

#### Haptics

This switch is stored and included in backups, but current interaction code does not read it. Changing it may have no visible effect in this version.

### Permissions

#### Overlay

Shows whether Display over other apps is On or Off. Tap to manage it.

#### Accessibility

Shows whether the MT Accessibility service is On or Off. Tap to open Android Accessibility settings.

#### Permissions tour

Replays the guided explanation for required permissions.

### Data

The section title shows a storage summary for saved presets and recent items.

#### Export

Creates a JSON backup through Android's file picker.

Default filename:

`mt-autoclicker-backup-YYYY-MM-DD.json`

The backup contains:

- Saved and recent presets
- Recorded macros
- Theme
- Analytics-enabled stored preference
- Notification-sound preference
- Haptic preference
- Marker size
- Remembered Screenshot app
- Remembered Auto Refresh app

The backup does not include:

- Device ID
- Notification Inbox messages/read state
- Android permission grants

#### Import

Select an MT Auto Clicker JSON backup.

Import behavior:

- New presets are merged by ID.
- New macros are merged by ID.
- Backup settings are applied.
- Existing items with the same ID are not duplicated.
- Older presets-only JSON files are also supported.

#### Clear

**Clear history** removes recent-run presets only. Saved presets remain.

#### Presets

**Delete all presets** removes saved and recent presets. Recorded macros remain.

#### Reset

Factory Reset removes:

- Presets
- Recent history
- Recorded macros
- Settings in the main settings store, including Device ID, theme, marker size, remembered apps, and preference switches

Android permission grants are controlled by Android and may remain enabled until you disable them in system settings.

Factory Reset does not clear server-side Inbox/read state, onboarding/tour state, or other CMS message-dismissal state.

### About

#### Version

Shows the installed app version.

#### Website

Opens:

`https://mtautoclicker.com`

#### Community

Opens:

`https://community.mtautoclicker.net`

#### Replay tour

Replays the onboarding tour.

If Overlay and Accessibility are already enabled, finishing the tour returns to Home instead of unnecessarily opening the Permissions screen.

#### Powered by WebTreta

Opens:

`https://www.webtreta.com/`

---

## 16. Backup, Import, and Data Removal

### Recommended backup routine

Export a backup:

- Before reinstalling the app
- Before Factory Reset
- Before moving to a new device
- After creating important macros or presets

### Restore on another device

1. Install MT Auto Clicker.
2. Open Settings.
3. Tap Import.
4. Select the backup JSON.
5. Re-enable Overlay and Accessibility.
6. Re-select remembered apps if package names differ.
7. Test every imported macro at slow speed.

Screen coordinates differ between devices. Imported Single/Multi targets and macro coordinates may need to be recorded again when screen size, orientation, navigation mode, or app layout changes.

### Before deleting data

- **Clear:** Only recent history
- **Presets:** All presets and recents, but not macros
- **Reset:** Presets, macros, and settings

These actions cannot be undone without a backup.

---

## 17. Tours, Review Prompts, and Helpful Messages

### Onboarding tour

Explains:

- Welcome and available tools
- Required permissions
- Basic target and Play workflow

### Permissions tour

Explains:

- Display over other apps
- Accessibility
- Returning to the app after permission setup

### What's New

The app may display a What's New card once for a new version or content update.

### Review prompt

After several feature uses, the app may ask whether you enjoy MT Auto Clicker.

Possible actions include:

- Rate on Play
- Remind later
- Not now / dismiss

### Feedback nudge

The app may later invite you to submit feedback. You can always open Feedback from the bottom dock.

---

## 18. Real-Life Examples

### Example A: Tap one game button 100 times

Feature: Single Target

- Target type: Point
- Interval: 250 ms
- Stop type: Cycles
- Cycles: 100

Place the target on the button and press Play.

### Example B: Repeat a three-step test

Feature: Multi Target

- Parallel: Off
- Interval: 1 second
- Stop type: Cycles
- Cycles: 20

Place three targets in order. The full sequence repeats 20 times.

### Example C: Trigger several independent controls

Feature: Multi Target

- Parallel: On
- Interval: 2 seconds
- Stop type: Duration
- Duration: 60 seconds

Each cycle triggers every placed target.

### Example D: Capture a long receipt

Feature: Full Page Screenshot

1. Select the shopping or browser app.
2. Start and allow Entire screen.
3. Open the receipt at its top.
4. Tap Screenshot.
5. Wait for the Gallery notification.

### Example E: Refresh a game lobby

Feature: Auto Refresh

- Interval: 10 seconds
- Duration: 5 minutes
- App: The game

Start, then tap Refresh on the floatbar. Stop immediately if the game does not use pull-to-refresh.

### Example F: Record a repetitive navigation path

Feature: Macro Recorder

1. Start Record.
2. Open the target app.
3. Tap the same sequence you normally use.
4. Stop from the notification.
5. Play at 0.5x.
6. If reliable, try 1x.

### Example G: Add natural timing variation

Feature: Single or Multi Target

- Variable interval: On
- Min: 800 ms
- Max: 1200 ms
- Random offset: 2 px

The timing and location vary slightly. Confirm the target is large enough for the offset.

---

## 19. Troubleshooting

### Floatbar does not appear

1. Open Settings → Permissions.
2. Confirm Overlay is On.
3. Confirm Accessibility is On.
4. Return to the feature and press Start again.
5. Check whether the phone placed MT Auto Clicker in battery-restricted mode.
6. Restart the app if Android recently updated it.

### Floatbar appears, but taps do not happen

1. Confirm Accessibility is enabled.
2. Turn the Accessibility service off and on again.
3. Make sure a target is placed.
4. Make sure Play was pressed.
5. Increase the interval.
6. Test in a simple app such as Calculator.
7. The target app may reject Accessibility-generated gestures.

### Play is disabled in Single/Multi Target

No target is currently placed.

Tap **+**, then tap or drag the desired location.

### The target is in the wrong place

- Screen orientation may have changed.
- The target app's layout may have moved.
- Display size/font scaling may have changed.
- A preset may have come from another device.

Remove and place the target again.

### Clicks are too fast or unreliable

- Use at least 10 ms; 100–250 ms is usually more reliable.
- Try 100–250 ms.
- Close heavy apps.
- Disable battery saver temporarily.
- Use 1 second when the target app needs time to update.

### Variable interval behaves unexpectedly

- Confirm Min is lower than or equal to Max.
- Confirm both values use the selected unit.
- Reopen Setup if settings were edited from the compact floatbar panel.

### Keyboard will not close

Use one of these:

- Tap the check icon in the number field.
- Tap outside the field.
- Scroll.
- Press Android Back.
- Move to another field and tap Done.

### Floatbar stays visible over MT Auto Clicker

Tap the MT logo on the floatbar. It should open the matching feature screen and dismiss that floatbar.

If it remains:

1. Tap Close on the floatbar.
2. Return to Home.
3. Force stop MT Auto Clicker only as a last resort.

### Auto Refresh does nothing

- Confirm the target page supports pull-to-refresh.
- Scroll the page to a normal resting position.
- Increase the interval.
- Confirm Accessibility is on.
- Try the same pull-down gesture manually.

### Auto Refresh refreshes the wrong screen

The chosen app may have opened to a different page. Navigate to the desired page before pressing Refresh on the floatbar.

### Full Page Screenshot asks for permission every time

Android controls screen-capture permission and may require confirmation for each new capture session. This is expected on many Android versions.

### Screenshot stops after one frame

Possible causes:

- The page did not move after the automated swipe.
- You reached the bottom.
- The app blocks automated scrolling.
- The visible content did not change enough.

Try a clearly scrollable page and manually confirm that a swipe moves it.

### Screenshot contains repeated or cut sections

- Wait for content to load before starting.
- Do not touch the screen.
- Avoid moving video/animated content.
- Close sticky popups.
- Start at the top.
- Some apps use complex nested scrolling that cannot be stitched perfectly.

### Screenshot is not in Gallery

Check:

- Gallery → Pictures → MT Auto Clicker
- Files → Pictures → MT Auto Clicker
- Saved-capture notification

Allow media visibility time; some Gallery apps scan new files with a delay.

On Android 8–9, saving can fail because the current build does not request the older storage-write permission. Use Android 10 or newer for Full Page Screenshot until that compatibility issue is fixed.

### Macro recording notification is missing

On Android 13 or newer, allow notification permission before recording. Recording has no floating Stop button, so stopping can be difficult when notifications are denied or disabled.

### Macro does not record a system gesture

- Use the recording notification's system-action controls.
- Toggle Accessibility off and on after an app update.
- OEM gesture navigation may reserve edge touches.
- Try 3-button navigation temporarily.

Home, Recents, and Notifications are recorded for later playback but are not executed live during recording. Back is the only global action deliberately executed while recording.

### Single/Multi pause returns to Ready

Pause is race-sensitive in the current click service. If the loop reaches its boundary while paused, the run ends. Press Play again to start another run.

### Macro playback taps the wrong location

Coordinates can change when:

- Orientation changes
- Screen resolution/display size changes
- Navigation mode changes
- The target app redesigns its interface
- A macro is moved to another device

Record the macro again in the current layout.

### Macro playback is too fast

Choose **0.5x** in the playback floatbar.

### Macro playback is ignored by another app

Some secure, banking, game, streaming, or protected apps reject Accessibility-generated input. MT Auto Clicker cannot override the target app's security rules.

### Preset has no app

The selected app package is missing from an older or invalid preset. Load the preset, choose an installed app, and save a new preset.

### Imported preset or macro does not work

- Confirm the same target app is installed.
- App package names can differ by region/version.
- Re-place screen coordinates.
- Re-record macros.
- Check permissions.

### Notifications do not load

1. Check internet access.
2. Open Inbox and tap Refresh.
3. Confirm the system date/time is correct.
4. Try again later if the server is unavailable.

### Feedback will not send

- Message must contain at least 10 characters.
- Select required related features.
- Check internet access.
- Shorten the message to 500 characters or less.
- Avoid repeatedly sending the same feedback.

### Permission shows Off after enabling

1. Return fully to MT Auto Clicker.
2. Wait a moment for status refresh.
3. Reopen Permissions.
4. Toggle the permission off and on.
5. Restart the phone if Android settings are stuck.

### Realme, Xiaomi, Oppo, Vivo, Samsung, and other OEM issues

Some manufacturers add battery and background restrictions.

If automation stops when the app is in the background:

1. Open Android Settings → Apps → MT Auto Clicker.
2. Set battery usage to **Unrestricted** or **Allow background activity**.
3. Allow notifications.
4. Allow Display over other apps.
5. Keep Accessibility enabled.
6. Remove MT Auto Clicker from automatic sleep/deep-sleep lists.

Menu names differ by manufacturer.

### Chromebook notes

- Enable Android app support / Google Play.
- Accessibility settings may be under Android settings inside ChromeOS.
- Window resizing can change target coordinates.
- Use a stable window size before placing targets or recording macros.

---

## 20. Privacy and Safety

### Device ID

MT Auto Clicker creates a random Device ID for this installation. It is not your phone serial number.

It is used for app services such as:

- Feedback replies
- Device-targeted Inbox notifications
- Pseudonymous usage events tied to this installation's Device ID

Feature-start tracking can send the Device ID, app version, Android version/device-model label, platform label, session ID, event name, and event metadata. The stored analytics preference is currently not checked by the tracking service.

### Accessibility

Accessibility is required to perform gestures. Use MT Auto Clicker only when you understand and accept Android's Accessibility warning.

The app is not intended to collect passwords. Do not automate or record sensitive fields.

### Feedback

Feedback includes:

- Your message
- Selected feedback category and features
- App version
- Android/device model label
- Random Device ID

Do not include personal or confidential information.

### Local data

Presets, macros, and settings are stored locally on the device. Exported backups are readable JSON files, so store them safely.

---

## 21. Quick Reference

### Start Single Target

`Home → Single Target → Configure → Start → + → Tap position → Play`

### Start Multi Target

`Home → Multi Target → Configure → Start → + → Place each target → Play`

### Record a macro

`Home → Macro Recorder → Record → Start Record → Confirm → Perform actions → Notification → Stop`

### Play a macro

`Macro Recorder → Playback → Choose macro → Play → Choose speed → Play`

### Full Page Screenshot

`Home → Full Page Screenshot → Choose app → Start → Entire screen → Open content → Screenshot icon`

### Auto Refresh

`Home → Auto Refresh → Set interval/stop → Choose app → Start → Refresh icon`

### Save a preset

`Feature → Recent → Save → Name → Presets`

### Open a feature from its floatbar

`Tap MT logo → matching feature opens → floatbar session closes`

### Get a feedback reply

`Feedback → Send → Inbox → Refresh`

### Back up everything important

`Settings → Data → Export`

---

## Support

- Website: [MTAutoclicker.com](https://mtautoclicker.com)
- Community: [community.mtautoclicker.net](https://community.mtautoclicker.net)
- In-app support: **Feedback**

