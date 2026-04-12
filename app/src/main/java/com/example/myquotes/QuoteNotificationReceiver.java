package com.example.myquotes;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class QuoteNotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "QuoteNotificationReceiver";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_SHOW_NOTIFICATION = "com.example.myquotes.SHOW_QUOTE_NOTIFICATION";
    public static final String ACTION_SNOOZE = "com.example.myquotes.SNOOZE_NOTIFICATION";
    public static final String EXTRA_QUOTE_ID = "quote_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        Log.d(TAG, "Receiver called - Action: " + action);

        if (ACTION_SNOOZE.equals(action)) {
            int quoteId = intent.getIntExtra(EXTRA_QUOTE_ID, -1);

            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
            }

            // Snooze for 60 minutes with the same quote ID
            QuoteNotificationScheduler.scheduleSnoozeNotification(context, 60, quoteId);
            Log.d(TAG, "Notification snoozed for quote #" + quoteId);

        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // Belt-and-suspenders re-schedule after reboot (WorkManager already survives reboots)
            if (QuoteNotificationScheduler.areNotificationsEnabled(context)) {
                QuoteNotificationScheduler.scheduleDailyNotification(context);
                Log.d(TAG, "Rescheduled daily notification after boot");
            } else {
                Log.d(TAG, "Notifications disabled — skipping reschedule");
            }
        }
    }
}
