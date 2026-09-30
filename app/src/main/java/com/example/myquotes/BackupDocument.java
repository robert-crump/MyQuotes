package com.example.myquotes;

import com.example.myquotes.notifications.NotificationHistory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * The file format for backups and manual export/import (#37): the quote list plus the
 * daily-notification history, as {@code {version: 3, quotes: [...], notificationHistory: {...}}}.
 * v3 (#49) writes each quote's {@code "tags"} instead of {@code "category"}. Decoding also
 * accepts v2 and v1 envelopes and legacy bare arrays (a category becomes one tag); with no
 * history section the history is empty. The Quote Store keeps using plain {@link QuoteCodec}.
 */
public final class BackupDocument {
    private static final int VERSION = 3;

    public final List<Quote> quotes;
    public final NotificationHistory history;

    public BackupDocument(List<Quote> quotes, NotificationHistory history) {
        this.quotes = quotes;
        this.history = history;
    }

    public String encodePretty() {
        try {
            JSONObject envelope = new JSONObject();
            envelope.put("version", VERSION);
            envelope.put("quotes", QuoteCodec.encodeArray(quotes));
            envelope.put("notificationHistory", history.toJson());
            return envelope.toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException("Unexpected JSON encoding failure", e);
        }
    }

    public static BackupDocument decode(String json) throws QuoteCodecException {
        if (json == null || json.trim().isEmpty()) {
            throw new QuoteCodecException("JSON string is null or empty");
        }
        String trimmed = json.trim();
        try {
            if (trimmed.charAt(0) == '[') {
                return new BackupDocument(QuoteCodec.parseQuotesArray(new JSONArray(trimmed)),
                        new NotificationHistory());
            }
            if (trimmed.charAt(0) != '{') {
                throw new QuoteCodecException("Unexpected JSON structure");
            }
            JSONObject envelope = new JSONObject(trimmed);
            List<Quote> quotes = QuoteCodec.parseQuotesArray(envelope.getJSONArray("quotes"));
            JSONObject history = envelope.optJSONObject("notificationHistory");
            return new BackupDocument(quotes, history == null
                    ? new NotificationHistory()
                    : NotificationHistory.fromJson(history));
        } catch (JSONException e) {
            throw new QuoteCodecException("Failed to parse JSON", e);
        }
    }
}
