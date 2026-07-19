# AccessibilityService declaration

## Classification

- The app is **not** an accessibility tool for people with disabilities.
- `android:isAccessibilityTool` is explicitly `false`.
- Primary purpose: deterministic, user-configured tap and gesture automation.

## Why AccessibilityService is necessary

Android does not provide a narrower public API that can inject user-configured
touch gestures into other apps. AccessibilityService is used to:

1. Dispatch taps at positions explicitly placed by the user.
2. Dispatch swipes, holds and pull-to-refresh gestures configured by the user.
3. Replay user-recorded macro actions.
4. Perform Back, Home and Recents only when those actions exist in a recorded macro.
5. Inspect the active window and focused editable field while macro recording so
   the app can capture text actions requested by the user.

The service does not autonomously plan, initiate or decide actions. It does not
change system settings, bypass privacy controls, or use Accessibility data for
advertising.

## Prominent disclosure and consent

Before the app opens Android Accessibility settings for the first time, it shows
an in-app modal titled **Before enabling Accessibility**. The modal explains:

- which actions the service performs;
- that active-window and focused-field content can be inspected during recording;
- that Accessibility content is processed on-device;
- that the content is not sold, shared or used for advertising;
- that the user can decline.

The user must tap **I understand — continue**. Dismissing the dialog, tapping
**Not now**, Back, or Home does not count as consent.

## Declaration video shot list

Record an unedited video with visible taps:

1. Launch the app from a fresh install.
2. Open Single Target and tap Start.
3. Show the Permissions screen.
4. Tap **Open Accessibility**.
5. Keep the disclosure visible long enough for every paragraph to be readable.
6. Tap **Not now** once to demonstrate decline.
7. Tap **Open Accessibility** again, then **I understand — continue**.
8. Enable MT Auto Clicker in Android system settings.
9. Return to the app, place one target, run it, pause it and stop it.
10. Open Macro Recorder briefly to show the feature whose focused-field access is disclosed.

Upload the video as an unlisted YouTube URL for the Play Console declaration.
