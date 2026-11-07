package com.example.myquotes;

import android.content.Context;

import com.example.myquotes.notifications.NotificationHistory;
import com.example.myquotes.notifications.QuoteNotifications;

import java.util.List;

/**
 * The Backup Document round-trip between app state and a file (#68): {@link #snapshot()} for
 * export, auto-backup and Back up now, {@link #replace} for import. Owned by
 * {@link MyApplication}; JVM-testable through its seams (Quote Store, Quote Collection,
 * {@link HistoryStore}).
 */
public final class BackupRoundTrip {

    /** Load/replace seam for the daily-notification history. */
    public interface HistoryStore {
        NotificationHistory load();

        void replace(NotificationHistory history);
    }

    /** The production {@link HistoryStore}, backed by {@link QuoteNotifications}. */
    public static HistoryStore notificationHistory(Context context) {
        Context app = context.getApplicationContext();
        return new HistoryStore() {
            @Override
            public NotificationHistory load() {
                return QuoteNotifications.loadHistory(app);
            }

            @Override
            public void replace(NotificationHistory history) {
                QuoteNotifications.replaceHistory(app, history);
            }
        };
    }

    private final QuoteStore store;
    private final QuoteCollection collection;
    private final HistoryStore history;

    public BackupRoundTrip(QuoteStore store, QuoteCollection collection, HistoryStore history) {
        this.store = store;
        this.collection = collection;
        this.history = history;
    }

    /**
     * The stored quotes and the notification history. Read from the Quote Store, which the
     * Collection saves on every mutation, so any thread may call it (blocking I/O).
     */
    public BackupDocument snapshot() {
        List<Quote> quotes = store.load();
        return new BackupDocument(quotes, history.load());
    }

    /**
     * Replaces both the Quote Collection (and so the Store) and the notification history with
     * the document's: not merged, so a document without history clears it (#37). Main thread.
     */
    public void replace(BackupDocument document) {
        collection.setList(document.quotes);
        history.replace(document.history);
    }
}
