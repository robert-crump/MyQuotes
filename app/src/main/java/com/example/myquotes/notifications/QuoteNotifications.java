package com.example.myquotes.notifications;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.example.myquotes.R;
import com.example.myquotes.scheduling.WorkChain;

import java.util.Calendar;

/**
 * Public facade for the notification subsystem. All other modules talk to this class only;
 * the WorkManager worker, BroadcastReceiver, notification channel, AlarmManager migration,
 * SharedPreferences flag, and notification id are internal details.
 */
public final class QuoteNotifications {
    private static final String TAG = "QuoteNotifications";

    private static final String PREFS_NAME = "QuoteNotificationPrefs";
    private static final String KEY_ENABLED = "notifications_enabled";
    private static final String KEY_MIGRATED_TO_WORKMANAGER = "migrated_to_workmanager";

    private static final String WORK_NAME_DAILY = "daily_quote_notification";

    static final String CHANNEL_ID = "daily_quote_channel";
    static final int NOTIFICATION_ID = 1001;

    public static final String EXTRA_QUOTE_ID = "quote_id";
    public static final int REQUEST_CODE_POST_NOTIFICATIONS = 100;

    private QuoteNotifications() {}

    /** Wire up notification channel, run one-time AlarmManager migration, re-schedule if enabled. */
    public static void initialize(Application app) {
        createChannel(app);
        migrateFromAlarmManager(app);
        if (isEnabled(app)) {
            scheduleDailyNotification(app);
        }
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (enabled) {
            scheduleDailyNotification(context);
        } else {
            cancelScheduledWork(context);
        }
    }

    public static void requestPostNotificationsPermission(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;

        activity.requestPermissions(
                new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_CODE_POST_NOTIFICATIONS);
    }

    /**
     * Applies the "permission granted, therefore enable" rule for a {@code POST_NOTIFICATIONS}
     * request. Returns null if the request code is not ours, otherwise whether notifications
     * are now enabled (false = denied, so the caller can tell the user).
     */
    public static Boolean onPermissionResult(Context context, int requestCode, int[] grantResults) {
        if (requestCode != REQUEST_CODE_POST_NOTIFICATIONS) return null;
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) setEnabled(context, true);
        return granted;
    }

    public static void promptBackgroundPermissionIfNeeded(Activity activity) {
        PowerManager powerManager = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        if (powerManager != null && powerManager.isIgnoringBatteryOptimizations(activity.getPackageName())) {
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.bg_permission_title)
                .setMessage(R.string.bg_permission_message)
                .setPositiveButton(R.string.bg_permission_ok, (dialog, which) -> openBatterySettings(activity))
                .setNegativeButton(R.string.bg_permission_ignore, (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Arms the daily notification as a {@link WorkChain} (ADR-002 amendment, #21): a pending
     * schedule is left alone, a missing one is healed. Called from every entry point (app open,
     * boot, enable); {@link DailyQuoteWorker} re-arms itself via {@link #rearmAfterRun}.
     */
    static void scheduleDailyNotification(Context context) {
        if (!isEnabled(context)) {
            Log.d(TAG, "Notifications are disabled");
            return;
        }

        long initialDelayMillis = calculateDelayTo4PM();
        WorkChain.arm(context, WORK_NAME_DAILY, DailyQuoteWorker.class, initialDelayMillis, null, null);
        Log.d(TAG, "Scheduled next daily notification targeting 4 PM (initial delay: "
                + (initialDelayMillis / 1000 / 60) + " minutes)");
    }

    /** Called by the worker after each run to arm tomorrow's occurrence behind the running one. */
    static void rearmAfterRun(Context context) {
        if (!isEnabled(context)) return;
        WorkChain.rearmFromWorker(context, WORK_NAME_DAILY, DailyQuoteWorker.class,
                calculateDelayTo4PM(), null, null);
    }

    private static void cancelScheduledWork(Context context) {
        WorkChain.cancel(context, WORK_NAME_DAILY);
        Log.d(TAG, "Cancelled all scheduled notifications");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static long calculateDelayTo4PM() {
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, 16);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        if (target.getTimeInMillis() <= System.currentTimeMillis()) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }
        return target.getTimeInMillis() - System.currentTimeMillis();
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Daily Quote", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Daily motivational quotes");

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    private static void migrateFromAlarmManager(Context context) {
        SharedPreferences prefs = prefs(context);
        if (prefs.getBoolean(KEY_MIGRATED_TO_WORKMANAGER, false)) return;

        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                Intent intent = new Intent(context, QuoteNotificationReceiver.class);
                PendingIntent daily = PendingIntent.getBroadcast(
                        context, 0, intent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (daily != null) { alarmManager.cancel(daily); daily.cancel(); }
            }
            Log.d(TAG, "Migrated from AlarmManager to WorkManager");
        } catch (Exception e) {
            Log.w(TAG, "Error during AlarmManager migration (non-fatal)", e);
        }

        prefs.edit().putBoolean(KEY_MIGRATED_TO_WORKMANAGER, true).apply();
    }

    private static void openBatterySettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + activity.getPackageName()));
        activity.startActivity(intent);
    }
}
