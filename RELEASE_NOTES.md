# Release notes

**[Русская версия](RELEASE_NOTES.ru.md)**

---

## 2.2.3

### Fixes

- Fixed an Android Gradle Plugin manifest merger error in the `core` module: removed an obsolete `package=` attribute and a dead, duplicate `LauncherActivity` declaration (the real activity only lives in the `app` module).
- Fixed a GitHub Actions CI warning about running on the deprecated Node.js 20 runtime by updating `android-actions/setup-android` and `actions/upload-artifact` to current versions.

### New

- Added a full English translation (`values-en`) alongside the existing Russian default — the app now has real multi-language support.
- Added a `Dockerfile` so the APK can be built in a container without installing the Android SDK locally.

### Code quality & tooling

- Enabled the Gradle Dependency Updates Plugin (`./gradlew dependencyUpdates`) to track outdated dependencies going forward.
- Refactored `AlarmScheduler`'s delayed resync retry from `Handler`/`postDelayed` to Kotlin Coroutines.
- Replaced the Dismiss/Snooze string commands sent between phone and watch with a type-safe sealed class (`AlarmCommand`). The wire format sent over the Data Layer is unchanged, so this release stays compatible with an older app version on the other device.

---

## 2.2.2

### Bug fixes

- **Alarm source filtering could block all alarms from syncing** on Android 11 and below (or whenever the alarm's source app couldn't be determined, e.g. missing `showIntent`). It now fails **open** (allows the sync) instead of failing closed.
- Fixed empty **"Set alarm" handler** detection on Android 11+: added the required `<queries>` declaration for `ACTION_SET_ALARM` (package visibility).
- Watch: rescheduling alarms after **reboot** no longer risks freezing the UI — the Data Layer read now runs off the main thread.
- Watch: the full-screen alarm notification now checks the **notification permission** before posting and no longer risks a crash if permission is missing or revoked.
- Watch: the keep-alive foreground service no longer crashes if the OS denies `startForeground` (stops itself instead).
- Fixed a possible **crash on Android 8.0/8.1 watches**: the launcher screen referenced a theme that only exists starting Android 10.
- Fixed an invalid notification-channel importance constant for the alarm channel (was using a non-existent value; now `IMPORTANCE_HIGH`).
- Hardened `NextAlarmReceiver` against spoofed intents with no/incorrect action.
- Added missing Russian translations for two strings that showed up in English in an otherwise Russian UI.

### Build, tests & release safety

- Release builds now **refuse to silently fall back to debug signing** when `keystore.properties` is missing, unless you explicitly pass `-PallowDebugSignedRelease=true`.
- Added a GitHub Actions **CI workflow**: unit tests, lint, and a debug build run on every push/PR.
- Added unit tests for the sync, vibration, and midnight-filter logic.
- Fixed all lint errors and cleaned up ~20 unused resources (dead strings, colors, duplicate icon files); remaining lint baseline is limited to a handful of dependency-version warnings blocked by the current AGP version.
- Bumped AndroidX Core, AppCompat, Material, Play Services Wearable, and Coroutines to their latest AGP-compatible versions.

---

## 2.2.1

### Phone UI

- Sync progress lines use clearer labels with green checkmarks: **Reading alarms : OK** and **Data Layer : OK**.
- Removed the status message when the next alarm comes from an app outside the allowed sources list.
- Removed the selected-apps summary under the **Alarm sources** button (selection still works in the dialog).
- Updated screenshots; dropped the unused `phone-sources.png` image from README/docs.

---

## 2.2.0

### Alarm sources (phone)

- Main phone screen: **Alarm sources** button and a summary of which apps are allowed to sync to the watch.
- **Multi-select** per app (checkboxes) instead of a single “clock only” toggle.
- Default: only clock apps sync (Google Clock, MIUI Clock, Samsung Clock, etc., depending on what is installed).
- Discovery combines:
  - apps handling `SET_ALARM` (classic clock apps);
  - known calendar packages (`com.android.calendar`, Google Calendar, Samsung, MIUI, `com.android.providers.calendar`);
  - **observed** packages — saved automatically when an app becomes the system “next alarm”;
  - **manually added** packages via **Add package** in the dialog.
- Each entry shows **app label and package in parentheses**, e.g. `Clock (com.google.android.deskclock)`.
- If the next system alarm comes from an app **not in the list**, it is **not** sent to the watch; the phone screen explains why.
- Legacy **clock-only** preference (`sync_clock_app_only`) **migrates** to the new allowed-package list on first launch.

### Midnight filter

- Alarms at exactly **00:00** (often calendar / reminders) are still **not synced** to the watch — filter is always on.
- The phone shows a clear message instead of alarm time in that case.
- Alarm queue (`AlarmQueueTracker`) uses the same midnight exclusion.

### Watch alarm screen

- **Dismiss** and **Snooze** buttons are color-coded: green and red (`ColoredAlarmButton`).
- Dismiss/snooze behavior is unchanged.

### Technical notes

- New types: `AlarmSyncPrefs`, `AlarmSourceApps`, `AlarmSourceRecognizer`, `ColoredAlarmButton`.
- Alarm source is inferred from `showIntent.creatorPackage` (API 31+); without it, per-app filtering may not apply.
- Android exposes only **one** system-wide “next alarm” — a platform limitation.

---

## 2.1.14

Previous release. See commit history and [README.md](README.md).
