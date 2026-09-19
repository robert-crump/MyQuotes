package com.example.myquotes.backup;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

/**
 * Facade for the local-folder {@link BackupDestination}: owns the enabled flag and the persisted
 * SAF folder permission. The run, schedule, state and failure notification are the shared Backup
 * module's.
 */
public final class LocalBackup {
    private static final BackupTarget TARGET = BackupTarget.LOCAL;
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_FOLDER_URI = "folder_uri";

    private LocalBackup() {}

    /** Wire up the failure notification channel and re-arm if enabled. */
    public static void initialize(Application app) {
        BackupScheduler.prepare(app, TARGET);
        if (isEnabled(app)) {
            BackupScheduler.arm(app, TARGET);
        }
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (enabled) {
            BackupScheduler.arm(context, TARGET);
        } else {
            BackupScheduler.cancel(context, TARGET);
        }
    }

    public static boolean hasFolderSelected(Context context) {
        return prefs(context).getString(KEY_FOLDER_URI, null) != null;
    }

    /** Persists the folder permission (survives restarts) and remembers the chosen folder. */
    public static void setFolder(Context context, Uri treeUri) {
        context.getContentResolver().takePersistableUriPermission(treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        prefs(context).edit().putString(KEY_FOLDER_URI, treeUri.toString()).apply();
    }

    /** Millis since epoch of the last successful backup, or 0 if there has never been one. */
    public static long getLastBackupTime(Context context) {
        return BackupState.lastTime(context, TARGET);
    }

    /** The destination to back up to, or null if disabled or no folder was chosen. */
    static BackupDestination destinationIfReady(Context context) {
        String uriString = prefs(context).getString(KEY_FOLDER_URI, null);
        if (!isEnabled(context) || uriString == null) return null;
        return new SafDestination(context, Uri.parse(uriString));
    }

    private static SharedPreferences prefs(Context context) {
        return BackupState.prefs(context, TARGET);
    }
}
