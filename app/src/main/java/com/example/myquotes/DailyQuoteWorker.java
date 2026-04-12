package com.example.myquotes;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.Random;

public class DailyQuoteWorker extends Worker {
    private static final String TAG = "DailyQuoteWorker";
    private static final String CHANNEL_ID = "daily_quote_channel";
    private static final int NOTIFICATION_ID = 1001;

    public DailyQuoteWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        if (!QuoteNotificationScheduler.areNotificationsEnabled(context)) {
            Log.d(TAG, "Notifications are disabled, skipping");
            return Result.success();
        }

        // Check for specific quote ID (from snooze)
        int specificQuoteId = getInputData().getInt("quote_id", -1);

        // Load quotes directly from SharedPreferences (NOT ViewModel -- fixes race condition)
        QuotePreferences prefs = new QuotePreferences(context);
        List<Quote> quotes = prefs.loadQuotes();

        if (quotes == null || quotes.isEmpty()) {
            Log.w(TAG, "No quotes available, retrying later");
            return Result.retry();
        }

        // Select quote: either the specific one (snooze) or a random one
        Quote selectedQuote;
        if (specificQuoteId != -1) {
            selectedQuote = null;
            for (Quote q : quotes) {
                if (q.getId() == specificQuoteId) {
                    selectedQuote = q;
                    break;
                }
            }
            if (selectedQuote == null) {
                Random random = new Random();
                selectedQuote = quotes.get(random.nextInt(quotes.size()));
            }
            Log.d(TAG, "Showing snoozed quote #" + specificQuoteId);
        } else {
            Random random = new Random();
            selectedQuote = quotes.get(random.nextInt(quotes.size()));
            Log.d(TAG, "Showing random quote #" + selectedQuote.getId());
        }

        showQuoteNotification(context, selectedQuote);
        return Result.success();
    }

    private void showQuoteNotification(Context context, Quote selectedQuote) {
        // Intent to open the app with this quote
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.putExtra(QuoteNotificationReceiver.EXTRA_QUOTE_ID, selectedQuote.getId());
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Snooze action with quote ID
        Intent snoozeIntent = new Intent(context, QuoteNotificationReceiver.class);
        snoozeIntent.setAction(QuoteNotificationReceiver.ACTION_SNOOZE);
        snoozeIntent.putExtra(QuoteNotificationReceiver.EXTRA_QUOTE_ID, selectedQuote.getId());

        PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Truncate quote text to ~150 chars
        String quoteText = selectedQuote.getQuoteText();
        if (quoteText.length() > 150) {
            quoteText = quoteText.substring(0, 147) + "...";
        }

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_quotation_24dp)
                .setContentTitle("Quote of the Day")
                .setContentText(quoteText)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(quoteText + "\n\n— " + selectedQuote.getAuthor()))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_quotation_24dp, "Snooze (60min)", snoozePendingIntent);

        // Show notification
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Log.d(TAG, "Notification shown for quote #" + selectedQuote.getId());
        } else {
            Log.e(TAG, "NotificationManager is null!");
        }
    }
}
