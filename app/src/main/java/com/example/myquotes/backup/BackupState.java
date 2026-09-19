package com.example.myquotes.backup;

import android.content.Context;
import android.content.SharedPreferences;

/** Last-backup time and content hash for one destination, in its existing SharedPreferences file. */
public final class BackupState {
    private static final String KEY_LAST_BACKUP_TIME = "last_backup_time";
    private static final String KEY_LAST_BACKUP_HASH = "last_backup_hash";

    private BackupState() {}

    static SharedPreferences prefs(Context context, BackupTarget target) {
        return context.getSharedPreferences(target.prefsName, Context.MODE_PRIVATE);
    }

    public static long lastTime(Context context, BackupTarget target) {
        return prefs(context, target).getLong(KEY_LAST_BACKUP_TIME, 0);
    }

    static String lastHash(Context context, BackupTarget target) {
        return prefs(context, target).getString(KEY_LAST_BACKUP_HASH, null);
    }

    static void record(Context context, BackupTarget target, String hash, long timeMillis) {
        prefs(context, target).edit()
                .putLong(KEY_LAST_BACKUP_TIME, timeMillis)
                .putString(KEY_LAST_BACKUP_HASH, hash)
                .apply();
    }
}
