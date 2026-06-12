package com.example.myquotes.notifications;

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

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // Belt-and-suspenders re-schedule after reboot (WorkManager already survives reboots).
            if (QuoteNotifications.isEnabled(context)) {
                QuoteNotifications.scheduleDailyNotification(context);
                Log.d(TAG, "Rescheduled daily notification after boot");
            }
        }
    }
}
