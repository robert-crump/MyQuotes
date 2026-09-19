package com.example.myquotes.backup;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.myquotes.R;
import com.example.myquotes.SettingsActivity;

/** Failure channel and notification for a backup destination; tapping it opens Settings. */
final class BackupNotifications {
    private BackupNotifications() {}

    static void createChannel(Context context, BackupTarget target) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel = new NotificationChannel(
                target.channelId, target.channelName, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(target.channelDescription);

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    static void notifyFailed(Context context, BackupTarget target) {
        Intent settingsIntent = new Intent(context, SettingsActivity.class);
        settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent settingsPendingIntent = PendingIntent.getActivity(
                context, 0, settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, target.channelId)
                .setSmallIcon(R.drawable.ic_quotation_24dp)
                .setContentTitle(context.getString(target.failedTitleRes))
                .setContentText(context.getString(target.failedMessageRes))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(settingsPendingIntent)
                .setAutoCancel(true);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(target.notificationId, builder.build());
        }
    }

    /** Clears a stale failure notification once a backup succeeds. */
    static void clearFailed(Context context, BackupTarget target) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(target.notificationId);
        }
    }
}
