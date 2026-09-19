# ADR-003: One backup module behind a Backup Destination seam

**Status:** Accepted (2026-09-19, issue #23)

## Context

Local and Drive auto-backup were the same code twice: enabled flag, schedule/cancel, failure channel, last-time/last-hash state, and the run itself (load, hash, skip, write, prune, record). The copies had drifted into two defects:

- Local wrote pretty-printed JSON, Drive wrote compact JSON, and both hashed the *compact* string, so the recorded hash never matched what was in the local folder.
- Both still used `PeriodicWorkRequest` with `UPDATE`, the shape ADR-002's amendment abandoned after the three-week silent outage (#21).

## Decision

1. **Backup Destination seam.** `BackupDestination` (check availability, write, list, delete) with two adapters, `SafDestination` and `DriveDestination`. `BackupRun` does the one encode/hash/skip/write/prune and returns an outcome; it has no Android state and is tested against an in-memory destination.
2. **Encoding chosen once:** pretty-printed (`QuoteCodec.encodePretty`), identical to manual export. **The hash is computed over the bytes actually written.** Consequence: the first run after upgrading sees a different hash than the stored compact one and writes one extra backup, then settles.
3. **One-shot chain for backups.** A shared `scheduling.WorkChain` is used by both the notification and each backup destination. Entry points arm with `KEEP`; the worker re-arms with `APPEND_OR_REPLACE` (see below). Backups run soon after arming, then 24 h after each run (no 4 PM target); Drive keeps `NetworkType.CONNECTED`. Old periodic jobs (`local_daily_backup`, `drive_daily_backup`) are cancelled on startup; the chains use new work names.
4. **Failure never breaks the chain.** `BackupWorker` posts the failure notification, re-arms, and returns `success`; a `failure` result would cancel the appended successor.
5. **State and ids unchanged:** `LocalBackupPrefs` / `DriveBackupPrefs` (including `folder_uri`, `enabled`), channel ids, notification ids 1002/1003. `DriveBackup.connect(email)` / `disconnect()` fold the auth flag and the schedule together so Settings makes one call per transition.

## Correction to ADR-002's amendment

ADR-002 says the worker re-arms with `ExistingWorkPolicy.KEEP`. Called from inside `doWork()`, the running request still counts as unfinished work under that unique name, so `KEEP` does nothing and the chain was only really re-armed by the next app open. `WorkChain.rearmFromWorker` uses `APPEND_OR_REPLACE` instead (the successor waits behind the running request); `KEEP` remains right for entry points. `DailyQuoteWorker` now uses it too. This is reasoned from WorkManager's documented policy semantics and has not been verified on a device.

## Alternatives rejected

**Two thin workers.** One `BackupWorker` selecting the target from input data is less code and one place for the failure/re-arm rules.
**Keeping compact encoding.** Manual export and the local folder were already pretty-printed; users may open these files.
