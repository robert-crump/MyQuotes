package com.example.myquotes.drive;

import android.app.Application;
import android.content.Context;

import com.example.myquotes.backup.BackupDestination;
import com.example.myquotes.backup.BackupScheduler;
import com.example.myquotes.backup.BackupState;
import com.example.myquotes.backup.BackupTarget;

/**
 * Facade for the Google Drive {@link BackupDestination}, layered on the connection state
 * {@link DriveAuth} owns. Settings makes one call per connect/disconnect transition; the run,
 * schedule, state and failure notification are the shared Backup module's.
 */
public final class DriveBackup {
    private static final BackupTarget TARGET = BackupTarget.DRIVE;

    private DriveBackup() {}

    /** Wire up the failure notification channel and re-arm if Drive is already connected. */
    public static void initialize(Application app) {
        BackupScheduler.prepare(app, TARGET);
        if (DriveAuth.isEnabled(app)) {
            BackupScheduler.arm(app, TARGET);
        }
    }

    /** Records the connected account and starts the daily upload chain. Call once authorization succeeds. */
    public static void connect(Context context, String accountEmail) {
        DriveAuth.markConnected(context, accountEmail);
        BackupScheduler.arm(context, TARGET);
    }

    /** Signs out, forgets the account and stops the daily upload chain. */
    public static void disconnect(Context context) {
        DriveAuth.disconnect(context);
        BackupScheduler.cancel(context, TARGET);
    }

    /** Millis since epoch of the last successful Drive backup, or 0 if there has never been one. */
    public static long getLastBackupTime(Context context) {
        return BackupState.lastTime(context, TARGET);
    }

    /** The destination to back up to, or null if Drive isn't connected. */
    public static BackupDestination destinationIfConnected(Context context) {
        return DriveAuth.isEnabled(context) ? new DriveDestination(context) : null;
    }
}
