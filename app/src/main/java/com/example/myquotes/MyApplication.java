package com.example.myquotes;

import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

public class MyApplication extends Application {
    private static final String TAG = "MyApplication";
    private static MyApplication instance;
    private QuoteCollection quoteCollection;

    private static final String PREFS_NAME = "AppSettings";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_MIGRATED_TO_WORKMANAGER = "migrated_to_workmanager";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Create notification channel early (moved from QuoteNotificationReceiver)
        createNotificationChannel();

        applyTheme();

        quoteCollection = new QuoteCollection(this);

        // One-time migration: cancel any legacy AlarmManager alarms
        migrateFromAlarmManager();

        // Re-schedule notifications if they were enabled (idempotent via WorkManager UPDATE policy)
        if (QuoteNotificationScheduler.areNotificationsEnabled(this)) {
            QuoteNotificationScheduler.scheduleDailyNotification(this);
        }
    }

    public static MyApplication getInstance() {
        return instance;
    }

    public QuoteCollection getQuoteCollection() {
        return quoteCollection;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Daily Quote";
            String description = "Daily motivational quotes";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel channel = new NotificationChannel(
                    "daily_quote_channel", name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager =
                    getSystemService(NotificationManager.class);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void migrateFromAlarmManager() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_MIGRATED_TO_WORKMANAGER, false)) {
            return;
        }

        // Cancel any old AlarmManager alarms
        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null) {
                Intent intent = new Intent(this, QuoteNotificationReceiver.class);
                // Cancel daily alarm (request code 0)
                PendingIntent dailyPending = PendingIntent.getBroadcast(
                        this, 0, intent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (dailyPending != null) {
                    alarmManager.cancel(dailyPending);
                    dailyPending.cancel();
                }
                // Cancel snooze alarm (request code 2)
                PendingIntent snoozePending = PendingIntent.getBroadcast(
                        this, 2, intent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (snoozePending != null) {
                    alarmManager.cancel(snoozePending);
                    snoozePending.cancel();
                }
            }
            Log.d(TAG, "Migrated from AlarmManager to WorkManager");
        } catch (Exception e) {
            Log.w(TAG, "Error during AlarmManager migration (non-fatal)", e);
        }

        prefs.edit().putBoolean(KEY_MIGRATED_TO_WORKMANAGER, true).apply();
    }

    public void applyTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_NO); // default: light
        AppCompatDelegate.setDefaultNightMode(themeMode);
    }

    public void setThemeMode(int mode) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public int getThemeMode() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_NO);
    }
}
