package com.example.myquotes.backup;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Last-backup time for one destination, in its existing SharedPreferences file. Display state
 * only: whether a run writes or skips is decided by {@link BackupRun} against the destination's
 * own content, not by anything recorded here.
 */
public final class BackupState {
    private static final String KEY_LAST_BACKUP_TIME = "last_backup_time";

    private BackupState() {}

    static SharedPreferences prefs(Context context, BackupTarget target) {
        return context.getSharedPreferences(target.prefsName, Context.MODE_PRIVATE);
    }

    public static long lastTime(Context context, BackupTarget target) {
        return prefs(context, target).getLong(KEY_LAST_BACKUP_TIME, 0);
    }

    static void record(Context context, BackupTarget target, long timeMillis) {
        prefs(context, target).edit().putLong(KEY_LAST_BACKUP_TIME, timeMillis).apply();
    }

    /** Forgets the last backup time, so Settings doesn't show a stale value for a new/disconnected account. */
    public static void clear(Context context, BackupTarget target) {
        prefs(context, target).edit().remove(KEY_LAST_BACKUP_TIME).apply();
    }
}
