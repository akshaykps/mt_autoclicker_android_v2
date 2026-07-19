# Google Play Data safety worksheet

Use this as the source of truth when completing the Play Console form. Verify it
again against the production backend before submission.

## Security practices

- Data is encrypted in transit: **Yes** (HTTPS endpoints only).
- Users can request deletion: **Yes**, by submitting a request through
  `https://www.mtautoclicker.com/feedback-form/` with the Device ID shown in
  Settings.
- Account creation: **No account is created in the Android app**.
- Independent security review: select **No** unless one is completed.

## Data collected

### Optional usage analytics — disabled by default

Purpose: Analytics.

- Device or other IDs: random app-generated Device ID.
- App interactions: app opens, feature-use events, tour and overlay actions.
- App info and performance: app version, Android version/SDK, device brand/model,
  CPU architecture, language, timezone and installation source.
- Approximate location: the backend may derive country from the request IP.

Collection is optional. The user can opt in or out under Settings →
**Optional usage analytics**. Opting out purges queued analytics events.

### User-submitted feedback and support

Purpose: App functionality and developer communications.

- Other user-generated content: feedback message and optional rating.
- Device or other IDs: Device ID used to associate support replies.
- App info and performance: app/device metadata attached for troubleshooting.

This data is sent only when the user submits feedback.

### Notification inbox actions

Purpose: App functionality.

- Device or other IDs: Device ID.
- App interactions: read/dismiss action for a notification.

## Data not collected by the Android app

- Name, email address or phone number
- Contacts
- Photos or videos
- Audio files or recordings
- Financial or payment information
- Passwords
- Precise location
- Advertising ID

## Accessibility data

Accessibility window/focused-field content used during macro recording is
processed on-device. It is not uploaded, sold, shared, or used for advertising.

## Sharing

No collected Android app data is sold. No advertising SDK is included. Data is
sent only to MT Auto Clicker's own backend/service providers required to operate
analytics, feedback and notification functionality; answer Play's “shared”
questions according to the final hosting/vendor arrangements.
