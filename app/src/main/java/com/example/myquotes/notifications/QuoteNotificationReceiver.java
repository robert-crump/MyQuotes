package com.example.myquotes.notifications;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

// Must be public: registered in AndroidManifest.xml.
public class QuoteNotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "QuoteNotificationReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Receiver called - Action: " + action);

        if (QuoteNotifications.ACTION_SNOOZE.equals(action)) {
            int quoteId = intent.getIntExtra(QuoteNotifications.EXTRA_QUOTE_ID, -1);

            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancel(QuoteNotifications.NOTIFICATION_ID);
            }

            QuoteNotifications.snooze(context, quoteId, 60);
            Log.d(TAG, "Notification snoozed for quote #" + quoteId);

        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // Belt-and-suspenders re-schedule after reboot (WorkManager already survives reboots).
            if (QuoteNotifications.isEnabled(context)) {
                QuoteNotifications.scheduleDailyNotification(context);
                Log.d(TAG, "Rescheduled daily notification after boot");
            }
        }
    }
}
