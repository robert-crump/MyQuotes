package com.example.myquotes.backup;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.myquotes.R;

import java.util.concurrent.TimeUnit;

/**
 * Public facade for the local auto-backup subsystem. All other modules talk to this class only;
 * the WorkManager worker, the persisted folder Uri, the failure notification channel, and the
 * enabled/last-backup SharedPreferences are internal details.
 */
public final class LocalBackup {
    private static final String TAG = "LocalBackup";

    private static final String PREFS_NAME = "LocalBackupPrefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_FOLDER_URI = "folder_uri";
    private static final String KEY_LAST_BACKUP_TIME = "last_backup_time";
    private static final String KEY_LAST_BACKUP_HASH = "last_backup_hash";

    private static final String WORK_NAME_DAILY_BACKUP = "local_daily_backup";

    static final String CHANNEL_ID = "backup_failure_channel";
    static final int FAILURE_NOTIFICATION_ID = 1002;

    private LocalBackup() {}

    /** Wire up the failure notification channel and re-schedule if enabled. */
    public static void initialize(Application app) {
        createChannel(app);
        if (isEnabled(app)) {
            scheduleDailyBackup(app);
        }
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (enabled) {
            scheduleDailyBackup(context);
        } else {
            cancelScheduledWork(context);
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

    static Uri getFolderUri(Context context) {
        String uriString = prefs(context).getString(KEY_FOLDER_URI, null);
        return uriString != null ? Uri.parse(uriString) : null;
    }

    /** Millis since epoch of the last successful backup, or 0 if there has never been one. */
    public static long getLastBackupTime(Context context) {
        return prefs(context).getLong(KEY_LAST_BACKUP_TIME, 0);
    }

    static String getLastBackupHash(Context context) {
        return prefs(context).getString(KEY_LAST_BACKUP_HASH, null);
    }

    static void recordSuccessfulBackup(Context context, String contentHash) {
        prefs(context).edit()
                .putLong(KEY_LAST_BACKUP_TIME, System.currentTimeMillis())
                .putString(KEY_LAST_BACKUP_HASH, contentHash)
                .apply();
    }

    static void scheduleDailyBackup(Context context) {
        if (!isEnabled(context)) {
            Log.d(TAG, "Auto-backup is disabled");
            return;
        }

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                LocalBackupWorker.class,
                24, TimeUnit.HOURS
        ).build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_DAILY_BACKUP,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
        );
        Log.d(TAG, "Scheduled daily local backup");
    }

    private static void cancelScheduledWork(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_DAILY_BACKUP);
        Log.d(TAG, "Cancelled scheduled local backup");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Backup Alerts", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Alerts when an automatic quote backup fails");

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    static void notifyBackupFailed(Context context) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_quotation_24dp)
                .setContentTitle(context.getString(R.string.local_backup_failed_title))
                .setContentText(context.getString(R.string.local_backup_failed_message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(FAILURE_NOTIFICATION_ID, builder.build());
        }
    }
}
