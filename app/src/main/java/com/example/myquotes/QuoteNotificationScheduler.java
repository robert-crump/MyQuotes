package com.example.myquotes;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class QuoteNotificationScheduler {
    private static final String TAG = "QuoteNotificationScheduler";
    private static final String PREFS_NAME = "QuoteNotificationPrefs";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";

    private static final String WORK_NAME_DAILY = "daily_quote_notification";
    private static final String WORK_NAME_SNOOZE = "snooze_quote_notification";

    public static void scheduleDailyNotification(Context context) {
        if (!areNotificationsEnabled(context)) {
            Log.d(TAG, "Notifications are disabled");
            return;
        }

        // Target 4 PM delivery: WorkManager fires during the last <flex> of the period,
        // so an 8-hour flex window ending at 4 PM produces an 8 AM–4 PM delivery window.
        long initialDelayMillis = calculateDelayTo4PM();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                DailyQuoteWorker.class,
                24, TimeUnit.HOURS,
                8, TimeUnit.HOURS   // flex window: last 8 h of period = 8 AM–4 PM
        )
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_DAILY,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
        );

        Log.d(TAG, "Scheduled daily notification via WorkManager targeting 4 PM (initial delay: "
                + (initialDelayMillis / 1000 / 60) + " minutes)");
    }

    public static void scheduleSnoozeNotification(Context context, int delayMinutes, int quoteId) {
        Data inputData = new Data.Builder()
                .putInt("quote_id", quoteId)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(DailyQuoteWorker.class)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_SNOOZE,
                ExistingWorkPolicy.REPLACE,
                workRequest
        );

        Log.d(TAG, "Scheduled snooze notification for " + delayMinutes + " minutes with quote #" + quoteId);
    }

    public static void cancelNotifications(Context context) {
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.cancelUniqueWork(WORK_NAME_DAILY);
        workManager.cancelUniqueWork(WORK_NAME_SNOOZE);
        Log.d(TAG, "Cancelled all notifications");
    }

    public static void setNotificationsEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply();

        if (enabled) {
            scheduleDailyNotification(context);
        } else {
            cancelNotifications(context);
        }
    }

    public static boolean areNotificationsEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, false);
    }

    private static long calculateDelayTo4PM() {
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, 16);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        // If 4 PM has already passed today, target tomorrow
        if (target.getTimeInMillis() <= System.currentTimeMillis()) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }

        return target.getTimeInMillis() - System.currentTimeMillis();
    }
}
