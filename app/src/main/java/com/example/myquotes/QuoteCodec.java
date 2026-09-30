package com.example.myquotes;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class QuoteCodec {
    private static final String TAG = "QuoteCodec";
    // 2: "tags" array instead of the free-text "category" (#49).
    private static final int VERSION = 2;

    private QuoteCodec() {}

    public static String encode(List<Quote> quotes) {
        try {
            return buildEnvelope(quotes).toString();
        } catch (JSONException e) {
            throw new IllegalStateException("Unexpected JSON encoding failure", e);
        }
    }

    public static List<Quote> decode(String json) throws QuoteCodecException {
        if (json == null || json.trim().isEmpty()) {
            throw new QuoteCodecException("JSON string is null or empty");
        }

        String trimmed = json.trim();
        char firstChar = trimmed.charAt(0);

        try {
            JSONArray quotesArray;
            if (firstChar == '[') {
                quotesArray = new JSONArray(trimmed);
            } else if (firstChar == '{') {
                JSONObject envelope = new JSONObject(trimmed);
                quotesArray = envelope.getJSONArray("quotes");
            } else {
                throw new QuoteCodecException("Unexpected JSON structure");
            }
            return parseQuotesArray(quotesArray);
        } catch (JSONException e) {
            throw new QuoteCodecException("Failed to parse JSON", e);
        }
    }

    private static JSONObject buildEnvelope(List<Quote> quotes) throws JSONException {
        JSONObject envelope = new JSONObject();
        envelope.put("version", VERSION);
        envelope.put("quotes", encodeArray(quotes));
        return envelope;
    }

    // Shared with BackupDocument, which wraps the same quote array in its own envelope.
    static JSONArray encodeArray(List<Quote> quotes) throws JSONException {
        JSONArray quotesArray = new JSONArray();
        for (Quote quote : quotes) {
            JSONObject jsonQuote = new JSONObject();
            jsonQuote.put("id", quote.getId());
            jsonQuote.put("author", quote.getAuthor());
            jsonQuote.put("quoteText", quote.getQuoteText());
            jsonQuote.put("source", quote.getSource());
            jsonQuote.put("tags", new JSONArray(quote.getTags()));
            jsonQuote.put("isFavorite", quote.isFavorite());
            jsonQuote.put("favoritedAt", quote.getFavoritedAt());
            jsonQuote.put("lastShown", quote.getLastShown());
            jsonQuote.put("timesShown", quote.getTimesShown());
            jsonQuote.put("addedAt", quote.getAddedAt());
            quotesArray.put(jsonQuote);
        }
        return quotesArray;
    }

    /** Whether {@code json} predates the current format (bare array or older envelope). */
    static boolean isOldFormat(String json) {
        if (json == null) return false;
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) return true;
        try {
            return new JSONObject(trimmed).optInt("version", 0) < VERSION;
        } catch (JSONException e) {
            return false;
        }
    }

    // "tags" when present; otherwise the legacy "category" as a single tag.
    private static List<String> parseTags(JSONObject jsonQuote) {
        List<String> tags = new ArrayList<>();
        JSONArray array = jsonQuote.optJSONArray("tags");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String tag = array.optString(i, "");
                if (!tag.isEmpty()) tags.add(tag);
            }
        } else {
            String tag = Hashtag.fromCategory(jsonQuote.optString("category", ""));
            if (!tag.isEmpty()) tags.add(tag);
        }
        return tags;
    }

    static List<Quote> parseQuotesArray(JSONArray jsonArray) {
        List<Quote> quotes = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonQuote = jsonArray.getJSONObject(i);
                if (!jsonQuote.has("id")) {
                    Log.w(TAG, "Skipping quote at index " + i + ": missing id");
                    continue;
                }
                int id = jsonQuote.getInt("id");
                String author = jsonQuote.optString("author", "");
                String quoteText = jsonQuote.optString("quoteText", "");
                String source = jsonQuote.optString("source", "");
                boolean isFavorite = jsonQuote.optBoolean("isFavorite", false);
                long favoritedAt = jsonQuote.optLong("favoritedAt", 0);
                long lastShown = jsonQuote.optLong("lastShown", 0);
                int timesShown = jsonQuote.optInt("timesShown", 0);
                long addedAt = jsonQuote.optLong("addedAt", 0);

                Quote quote = new Quote(id, author, quoteText, source);
                quote.setTags(parseTags(jsonQuote));
                quote.setFavorite(isFavorite);
                quote.setFavoritedAt(favoritedAt);
                quote.setLastShown(lastShown);
                quote.setTimesShown(timesShown);
                quote.setAddedAt(addedAt);
                quotes.add(quote);
            } catch (JSONException e) {
                Log.w(TAG, "Skipping quote at index " + i + ": " + e.getMessage());
            }
        }
        return quotes;
    }
}
