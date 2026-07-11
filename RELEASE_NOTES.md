# Release notes

**[Русская версия](RELEASE_NOTES.ru.md)**

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
