# MyQuotes — Domain Glossary

## Core domain objects

**Quote** — a single quotation with text, author, source, category, favorite status, view count, and a stable integer id.

**Quote Collection** — the canonical, ordered set of all quotes the user owns. Responsible for CRUD, id assignment, persistence, and notifying observers when the set changes. Implemented as a plain Java object (not a ViewModel) held by `MyApplication` for the lifetime of the process. The authoritative source of truth for quote data. Constructed over a Quote Store; owns bootstrap (`loadFromStore()`, called from `MyApplication.onCreate`, which also trims fields). Owns the mutation invariants: `edit` preserves favorite and view state, `toggleFavorite` is the only favorite path, empty lists are persisted. Background workers do not use the Collection; they read the Quote Store.

**Quote Store** — the persistence seam under the Quote Collection (`QuoteStore`): load the quote list (empty when nothing stored), save the list (including an empty one), and whether anything has ever been stored. Adapters: `SharedPreferencesQuoteStore` (`QuotePrefs/quotes_json`) in production, `InMemoryQuoteStore` in test sources. `MyApplication` builds the store and exposes it via `getQuoteStore()`; `DailyQuoteWorker` and `BackupWorker` read it by name instead of going through the Collection.

**Reading Session** — a shuffled traversal of the Quote Collection. Tracks deck order, current position, and the currently displayed quote. Subscribes to the Quote Collection via `observeForever` and reconciles its deck reactively: new quotes append to the end, deleted quotes are removed with position adjusted, updated quotes are replaced in place. Implemented as an Android `ViewModel` scoped to `MainActivity`. Other flows (Favorites, Search results) maintain their own ordered views and do not use a Reading Session.

**Deck** — the ordered list of quotes inside a Reading Session, in the order they will be presented to the user. Initially a shuffle of the full collection; mutated in place as the collection changes.

**Reading position** — the zero-based index of the currently displayed quote within the Deck.

## Flows

**Main browse flow** — ViewPager2 in MainActivity driven by the Reading Session's Deck.

**Favorites flow** — ViewPager2 in FavoritesActivity driven by a filtered, recency-sorted view of the Quote Collection (not a Reading Session).

**Search / category flow** — list or pager in SearchActivity / CategoriesActivity driven by a filtered view of the Quote Collection.

## Subsystems

**Quote Notifications** — the daily-quote notification feature. A single facade (`com.example.myquotes.notifications.QuoteNotifications`) owns the WorkManager scheduling, the notification channel, the boot-reschedule BroadcastReceiver, the runtime `POST_NOTIFICATIONS` permission flow, the battery-optimization dialog, and the enabled/disabled flag. The rest of the app interacts only with this facade. See ADR-002.

**Backup Destination** — where a backup run puts its files. An interface (`backup.BackupDestination`) with four operations: check availability, write bytes under a filename, list existing entries (name plus opaque handle), delete an entry. Two adapters: `SafDestination` (a user-chosen local folder via the Storage Access Framework) and `DriveDestination` (the app-owned "MyQuotes Backups" folder under My Drive, via `DriveRestClient`; auth and network happen lazily in `checkAvailable`).

**Backup run** — one backup of the quote list to one Backup Destination (`backup.BackupRun`, no Android state). Encodes pretty-printed (matching manual export), hashes the exact bytes to be written, skips if the hash equals the last recorded one, otherwise writes a `BackupFilename`-named file and prunes via `BackupRetention` to 9 files (7 daily + 1 weekly + 1 monthly). Returns an outcome (skipped-unchanged, written-with-hash, failed-with-cause); recording state and notifying is the caller's job.

**Auto-backup** — daily background backup to each enabled Backup Destination (local folder, Google Drive). One shared module in `backup`: `BackupWorker` (selects the destination from input data, loads quotes from the Quote Store, does a Backup run, records `BackupState` on success, posts `BackupNotifications` on failure, always re-arms and returns success), `BackupScheduler` (a `scheduling.WorkChain` per destination, 24 h apart; Drive requires any network), and `BackupTarget` (per-destination constants: prefs file `LocalBackupPrefs`/`DriveBackupPrefs`, channel id, notification ids 1002/1003). `LocalBackup` and `DriveBackup` are thin facades for the enabled flag / SAF folder permission / `DriveBackup.connect(email)` + `disconnect()`. See ADR-003.

**Google Drive auth** — auth plumbing for Drive-based backup. A single facade (`com.example.myquotes.drive.DriveAuth`) owns the enabled/connected-account SharedPreferences and a two-step connect flow: Credential Manager (`androidx.credentials`, Google ID token option) shows the account chooser and returns the signed-in account's email, then the Authorization API (`com.google.android.gms.auth.api.identity`) requests the `drive.file` scope, surfacing a consent `PendingIntent` when needed. `SettingsActivity` owns the `ActivityResultLauncher<IntentSenderRequest>` that launches that consent UI and feeds the result back into `DriveAuth.completeAuthorizationResult`. Disconnecting clears the stored account and calls `CredentialManager.clearCredentialStateAsync` to drop the cached credential state. The OAuth client ID (from #14) is wired in as `BuildConfig.DRIVE_OAUTH_CLIENT_ID` via `local.properties`. `DriveAuth.getAccessToken` re-runs the Authorization API silently (no UI) to mint a fresh token for background use; it throws if the grant needs to be re-resolved interactively (e.g. revoked).
