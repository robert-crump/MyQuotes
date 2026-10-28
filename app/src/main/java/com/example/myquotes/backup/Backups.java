package com.example.myquotes.backup;

import android.content.Context;

import com.example.myquotes.BackupDocument;
import com.example.myquotes.MyApplication;
import com.example.myquotes.Quote;
import com.example.myquotes.drive.DriveBackup;
import com.example.myquotes.notifications.QuoteNotifications;

import java.util.List;

/**
 * One backup of the Quote Store to a {@link BackupTarget}, shared by the daily {@link BackupWorker}
 * and Settings' "Back up now". A written backup records its time and clears the target's failure
 * notification; posting a failure is left to the caller. Blocking (I/O, and network for Drive):
 * call it off the main thread.
 */
public final class Backups {
    private Backups() {}

    /** Whether {@code target} is switched on and set up (a folder chosen, a Drive account connected). */
    public static boolean isReady(Context context, BackupTarget target) {
        return destination(context, target) != null;
    }

    /** The run's outcome; null when the target is off or not set up, or there are no quotes. */
    public static BackupRun.Outcome runOnce(Context context, BackupTarget target) {
        BackupDestination destination = destination(context, target);
        if (destination == null) return null;

        List<Quote> quotes = MyApplication.getInstance().getQuoteStore().load();
        if (quotes.isEmpty()) return null;

        BackupDocument document = new BackupDocument(quotes, QuoteNotifications.loadHistory(context));
        BackupRun.Outcome outcome = BackupRun.run(document, destination, System.currentTimeMillis());
        if (outcome.kind == BackupRun.Kind.WRITTEN) {
            BackupState.record(context, target, System.currentTimeMillis());
            BackupNotifications.clearFailed(context, target);
        }
        return outcome;
    }

    private static BackupDestination destination(Context context, BackupTarget target) {
        return target == BackupTarget.LOCAL
                ? LocalBackup.destinationIfReady(context)
                : DriveBackup.destinationIfConnected(context);
    }
}
