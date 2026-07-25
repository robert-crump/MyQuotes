# MyQuotes — Domain Glossary

## Core domain objects

**Quote** — a single quotation with text, author, source, category, favorite status, view count, and a stable integer id.

**Quote Collection** — the canonical, ordered set of all quotes the user owns. Responsible for CRUD, id assignment, persistence, and notifying observers when the set changes. Implemented as a plain Java object (not a ViewModel) held by `MyApplication` for the lifetime of the process. The authoritative source of truth for quote data.

**Reading Session** — a shuffled traversal of the Quote Collection. Tracks deck order, current position, and the currently displayed quote. Subscribes to the Quote Collection via `observeForever` and reconciles its deck reactively: new quotes append to the end, deleted quotes are removed with position adjusted, updated quotes are replaced in place. Implemented as an Android `ViewModel` scoped to `MainActivity`. Other flows (Favorites, Search results) maintain their own ordered views and do not use a Reading Session.

**Deck** — the ordered list of quotes inside a Reading Session, in the order they will be presented to the user. Initially a shuffle of the full collection; mutated in place as the collection changes.

**Reading position** — the zero-based index of the currently displayed quote within the Deck.

## Flows

**Main browse flow** — ViewPager2 in MainActivity driven by the Reading Session's Deck.

**Favorites flow** — ViewPager2 in FavoritesActivity driven by a filtered, recency-sorted view of the Quote Collection (not a Reading Session).

**Search / category flow** — list or pager in SearchActivity / CategoriesActivity driven by a filtered view of the Quote Collection.

## Subsystems

**Quote Notifications** — the daily-quote notification feature. A single facade (`com.example.myquotes.notifications.QuoteNotifications`) owns the WorkManager scheduling, the notification channel, the boot-reschedule BroadcastReceiver, the runtime `POST_NOTIFICATIONS` permission flow, the battery-optimization dialog, and the enabled/disabled flag. The rest of the app interacts only with this facade. See ADR-002.

**Local Auto-backup** — daily background backup of the quote collection to a user-chosen local folder. A single facade (`com.example.myquotes.backup.LocalBackup`) owns the WorkManager scheduling, the persisted SAF folder permission, the failure notification channel, and the enabled/last-backup-time/last-backup-hash state. The write path (`QuoteExporter.writeToUri`) is shared with the manual export flow in `SettingsActivity`. `LocalBackupWorker` skips writing when the collection is unchanged since the last successful backup (compared via a SHA-256 hash of the encoded quotes). Each backup is written under a timestamped filename (`BackupFilename`, matching the manual-export naming convention) rather than overwriting a single file; after each write, `BackupRetention` prunes the folder to the 9 most recent backups (7 daily + 1 weekly + 1 monthly), with the weekly/monthly slots simply the next 2 most recent backups aging out of the daily window rather than separate dedicated writes.

**Google Drive auth** — auth plumbing for Drive-based backup. A single facade (`com.example.myquotes.drive.DriveAuth`) owns the enabled/connected-account SharedPreferences and a two-step connect flow: Credential Manager (`androidx.credentials`, Google ID token option) shows the account chooser and returns the signed-in account's email, then the Authorization API (`com.google.android.gms.auth.api.identity`) requests the `drive.file` scope, surfacing a consent `PendingIntent` when needed. `SettingsActivity` owns the `ActivityResultLauncher<IntentSenderRequest>` that launches that consent UI and feeds the result back into `DriveAuth.completeAuthorizationResult`. Disconnecting clears the stored account and calls `CredentialManager.clearCredentialStateAsync` to drop the cached credential state. The OAuth client ID (from #14) is wired in as `BuildConfig.DRIVE_OAUTH_CLIENT_ID` via `local.properties`. `DriveAuth.getAccessToken` re-runs the Authorization API silently (no UI) to mint a fresh token for background use; it throws if the grant needs to be re-resolved interactively (e.g. revoked).

**Google Drive auto-backup** — daily background backup of the quote collection to a Drive folder, layered on `DriveAuth`'s connection state. A single facade (`com.example.myquotes.drive.DriveBackup`) owns the WorkManager scheduling (any network, not Wi-Fi only), the failure notification channel, and the last-backup-time/hash state; `SettingsActivity` calls `scheduleDailyBackup`/`cancelScheduledWork` at the same points it calls `DriveAuth.markConnected`/`disconnect`. `DriveBackupWorker` loads quotes from `QuotePreferences` (same race-avoidance as `LocalBackupWorker`), skips the upload when the SHA-256 hash of the encoded quotes is unchanged since the last successful Drive backup, mints an access token via `DriveAuth.getAccessToken`, and talks to the Drive REST API v3 through `DriveRestClient` - a small hand-rolled `HttpURLConnection` client (find-or-create folder, multipart upload, list, delete) that avoids pulling in the full google-api-client stack for a handful of calls. Backups share the local destination's timestamped filename convention and pruning logic (`backup.BackupFilename`, `backup.BackupRetention`, both made public for this reuse) against the files living in a single app-owned "MyQuotes Backups" folder under My Drive. A Drive failure (auth revoked, network error, etc.) posts a notification immediately and does not affect the local backup job, and vice versa.
