package com.example.myquotes.notifications;

import com.example.myquotes.Quote;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-quote daily-notification bookkeeping (#37): how often a quote was notified, how often that
 * notification was tapped, and when it was last notified. Keyed by quote id and kept apart from
 * the Quote Collection so the worker can write it without racing the Collection's saves.
 * Pure and mutable; {@link QuoteNotifications} owns persistence.
 */
public final class NotificationHistory {

    public static final class Entry {
        public final int notifiedCount;
        public final int clickedCount;
        public final long lastNotifiedAt;

        Entry(int notifiedCount, int clickedCount, long lastNotifiedAt) {
            this.notifiedCount = notifiedCount;
            this.clickedCount = clickedCount;
            this.lastNotifiedAt = lastNotifiedAt;
        }
    }

    // Sorted by id so the encoding is deterministic (the backup dedupe hashes it).
    private final Map<Integer, Entry> entries = new TreeMap<>();

    /**
     * The entry for this quote, or null if it has none. An entry older than the quote's
     * {@code addedAt} belonged to an earlier quote with the same (reused) id and is ignored.
     */
    public Entry entryFor(Quote quote) {
        Entry entry = entries.get(quote.getId());
        if (entry == null || entry.lastNotifiedAt < quote.getAddedAt()) return null;
        return entry;
    }

    void recordNotified(Quote quote, long nowMillis) {
        Entry previous = entryFor(quote);
        entries.put(quote.getId(), previous == null
                ? new Entry(1, 0, nowMillis)
                : new Entry(previous.notifiedCount + 1, previous.clickedCount, nowMillis));
    }

    /** Ignored if the quote has no entry (e.g. the history was replaced by an import since). */
    void recordClicked(int quoteId) {
        Entry previous = entries.get(quoteId);
        if (previous == null) return;
        entries.put(quoteId, new Entry(previous.notifiedCount, previous.clickedCount + 1,
                previous.lastNotifiedAt));
    }

    /** Drops entries for quotes that no longer exist. */
    void retainOnly(Collection<Integer> quoteIds) {
        entries.keySet().retainAll(quoteIds);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        for (Map.Entry<Integer, Entry> e : entries.entrySet()) {
            JSONObject entry = new JSONObject();
            entry.put("notifiedCount", e.getValue().notifiedCount);
            entry.put("clickedCount", e.getValue().clickedCount);
            entry.put("lastNotifiedAt", e.getValue().lastNotifiedAt);
            json.put(String.valueOf(e.getKey()), entry);
        }
        return json;
    }

    /** Entries with a non-numeric key or a non-object value are skipped. */
    public static NotificationHistory fromJson(JSONObject json) {
        NotificationHistory history = new NotificationHistory();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject entry = json.optJSONObject(key);
            if (entry == null) continue;
            try {
                history.entries.put(Integer.parseInt(key), new Entry(
                        entry.optInt("notifiedCount", 0),
                        entry.optInt("clickedCount", 0),
                        entry.optLong("lastNotifiedAt", 0)));
            } catch (NumberFormatException ignored) {
                // Not a quote id.
            }
        }
        return history;
    }
}
