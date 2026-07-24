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

**Local Auto-backup** — daily background backup of the quote collection to a user-chosen local folder. A single facade (`com.example.myquotes.backup.LocalBackup`) owns the WorkManager scheduling, the persisted SAF folder permission, the failure notification channel, and the enabled/last-backup-time/last-backup-hash state. The write path (`QuoteExporter.writeToUri`) is shared with the manual export flow in `SettingsActivity`. `LocalBackupWorker` skips writing when the collection is unchanged since the last successful backup (compared via a SHA-256 hash of the encoded quotes).
