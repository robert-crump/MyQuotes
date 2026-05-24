# ADR-002: Consolidate notification subsystem behind a QuoteNotifications module

**Status:** Accepted
**Date:** 2026-05-24

## Context

Understanding "how notifications work" required hopping between five files:

- `QuoteNotificationScheduler` — WorkManager scheduling, enabled-flag SharedPreferences
- `DailyQuoteWorker` — the Worker; hard-coded `NOTIFICATION_ID = 1001` and channel id
- `QuoteNotificationReceiver` — snooze + boot rescheduling; duplicate `NOTIFICATION_ID = 1001`
- `MyApplication` — notification channel creation, AlarmManager-to-WorkManager migration, re-schedule on app start
- `MainActivity` — `POST_NOTIFICATIONS` runtime permission flow
- `BackgroundPermissionHelper` — battery-optimization dialog (only relevant because WorkManager needs background freedom)
- `SettingsActivity` — the toggle UI

State was fragmented: the enabled flag lived in its own `QuoteNotificationPrefs`, the notification id and channel id were duplicated, and the migration flag lived in a different SharedPreferences file in `MyApplication`. Each module was a shallow shell — the receiver was a 30-line forwarder, the helper a 20-line dialog.

## Decision

A single `com.example.myquotes.notifications.QuoteNotifications` class is the only thing the rest of the app talks to. Public surface:

- `initialize(Application)` — call once from `MyApplication.onCreate`. Creates the channel, runs the one-time AlarmManager migration, re-schedules the daily worker if previously enabled.
- `isEnabled(Context)` / `setEnabled(Context, boolean)` — the toggle.
- `snooze(Context, int quoteId, int delayMinutes)` — schedule a one-shot reminder.
- `requestPostNotificationsPermission(Activity)` — Android 13+ runtime permission.
- `promptBackgroundPermissionIfNeeded(Activity)` — battery-optimization dialog (replaces `BackgroundPermissionHelper`).
- `EXTRA_QUOTE_ID` — the intent extra used to navigate to a specific quote.
- `REQUEST_CODE_POST_NOTIFICATIONS` — for `onRequestPermissionsResult` matching.

Hidden behind the facade and now package-private:

- `DailyQuoteWorker` (must stay `public` for WorkManager reflection, but lives in the notifications package)
- `QuoteNotificationReceiver` (must stay `public` for the manifest, but lives in the notifications package)
- `CHANNEL_ID`, `NOTIFICATION_ID`, `ACTION_SNOOZE` — single source of truth
- WorkManager scheduling helpers, the SharedPreferences names, the channel creation, the AlarmManager migration

## Consequences

- **Locality:** Notification timing/content/permissions live in one folder. `SettingsActivity` and `MainActivity` no longer reference WorkManager, BroadcastReceivers, or battery `PowerManager`.
- **Deduplication:** `NOTIFICATION_ID = 1001` and `CHANNEL_ID = "daily_quote_channel"` exist exactly once.
- **MyApplication shrinks:** ~50 lines of channel creation and AlarmManager migration code move out; `onCreate` becomes a 4-line ceremony.
- **Testability:** The notification path has one seam — `DailyQuoteWorker` can be exercised directly with a stub `QuotePreferences` and assertions against `NotificationManager`.
- **Migration flag relocated:** `migrated_to_workmanager` moved from the `AppSettings` SharedPreferences to `QuoteNotificationPrefs`. This is safe because the migration is idempotent: a user who already migrated will simply re-run the (no-op) cancel on their next launch and have the new flag set.

## Alternatives rejected

**Singleton instance of QuoteNotifications.** Rejected because the class has no per-instance state — all state is in SharedPreferences or WorkManager. Static methods with a `private` constructor match the actual shape.

**Keep `BackgroundPermissionHelper` separate.** Battery-optimization access is only requested because WorkManager needs it to fire on time. Folding it into the notifications module reflects the real reason the permission exists and removes a one-method helper that read as "general infrastructure".

**Put the receiver class in the main package.** Would have left a dangling notifications-related class outside the notifications folder. The manifest reference is a one-line change (`.notifications.QuoteNotificationReceiver`).
