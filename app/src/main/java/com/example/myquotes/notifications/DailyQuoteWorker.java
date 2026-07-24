package com.example.myquotes.notifications;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.myquotes.MainActivity;
import com.example.myquotes.Quote;
import com.example.myquotes.QuotePreferences;
import com.example.myquotes.R;

import java.util.List;
import java.util.Random;

// Must be public: WorkManager instantiates it via reflection.
public class DailyQuoteWorker extends Worker {
    private static final String TAG = "DailyQuoteWorker";

    public DailyQuoteWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        if (!QuoteNotifications.isEnabled(context)) {
            Log.d(TAG, "Notifications are disabled, skipping");
            return Result.success();
        }

        // Load quotes directly from SharedPreferences (NOT QuoteCollection -- fixes race condition)
        QuotePreferences prefs = new QuotePreferences(context);
        List<Quote> quotes = prefs.loadQuotes();

        if (quotes == null || quotes.isEmpty()) {
            Log.w(TAG, "No quotes available, retrying later");
            return Result.retry();
        }

        Quote selectedQuote = quotes.get(new Random().nextInt(quotes.size()));
        Log.d(TAG, "Showing random quote #" + selectedQuote.getId());

        showQuoteNotification(context, selectedQuote);
        return Result.success();
    }

    private void showQuoteNotification(Context context, Quote selectedQuote) {
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.putExtra(QuoteNotifications.EXTRA_QUOTE_ID, selectedQuote.getId());
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String quoteText = selectedQuote.getQuoteText();
        String author = selectedQuote.getAuthor();

        String title = TextUtils.isEmpty(author)
                ? context.getString(R.string.daily_quote_title)
                : context.getString(R.string.daily_quote_title_with_author, author);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, QuoteNotifications.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_quotation_24dp)
                .setContentTitle(title)
                .setContentText(QuoteTextUtils.truncate(quoteText, 150))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(QuoteTextUtils.truncate(quoteText, 300)))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(QuoteNotifications.NOTIFICATION_ID, builder.build());
            Log.d(TAG, "Notification shown for quote #" + selectedQuote.getId());
        } else {
            Log.e(TAG, "NotificationManager is null!");
        }
    }
}
