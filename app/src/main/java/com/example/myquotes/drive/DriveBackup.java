package com.example.myquotes.drive;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.myquotes.R;

import java.util.concurrent.TimeUnit;

/**
 * Public facade for the Google Drive auto-backup subsystem (the upload side, built on top of the
 * connection state {@link DriveAuth} owns). This class owns the WorkManager scheduling, the
 * failure notification channel, and the last-backup-time/hash state. All other modules talk to
 * this class only; {@link DriveBackupWorker} and {@link DriveRestClient} are internal details.
 */
public final class DriveBackup {
    private static final String TAG = "DriveBackup";

    private static final String PREFS_NAME = "DriveBackupPrefs";
    private static final String KEY_LAST_BACKUP_TIME = "last_backup_time";
    private static final String KEY_LAST_BACKUP_HASH = "last_backup_hash";

    private static final String WORK_NAME_DAILY_BACKUP = "drive_daily_backup";

    static final String CHANNEL_ID = "drive_backup_failure_channel";
    static final int FAILURE_NOTIFICATION_ID = 1003;

    private DriveBackup() {}

    /** Wire up the failure notification channel and re-schedule if Drive is already connected. */
    public static void initialize(Application app) {
        createChannel(app);
        if (DriveAuth.isEnabled(app)) {
            scheduleDailyBackup(app);
        }
    }

    /** Millis since epoch of the last successful Drive backup, or 0 if there has never been one. */
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

    /** Starts the daily upload job. Call once Drive connects (and on app start if already connected). */
    public static void scheduleDailyBackup(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                DriveBackupWorker.class,
                24, TimeUnit.HOURS
        ).setConstraints(constraints).build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_DAILY_BACKUP,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
        );
        Log.d(TAG, "Scheduled daily Drive backup");
    }

    /** Stops the daily upload job. Call when Drive disconnects. */
    public static void cancelScheduledWork(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_DAILY_BACKUP);
        Log.d(TAG, "Cancelled scheduled Drive backup");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Drive Backup Alerts", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Alerts when an automatic Google Drive backup fails");

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    static void notifyBackupFailed(Context context) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_quotation_24dp)
                .setContentTitle(context.getString(R.string.drive_backup_failed_title))
                .setContentText(context.getString(R.string.drive_backup_failed_message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(FAILURE_NOTIFICATION_ID, builder.build());
        }
    }
}
