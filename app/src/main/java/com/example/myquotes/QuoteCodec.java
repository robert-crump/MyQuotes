package com.example.myquotes;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class QuoteCodec {
    private static final String TAG = "QuoteCodec";
    private static final int VERSION = 1;

    private QuoteCodec() {}

    public static String encode(List<Quote> quotes) {
        try {
            return buildEnvelope(quotes).toString();
        } catch (JSONException e) {
            throw new IllegalStateException("Unexpected JSON encoding failure", e);
        }
    }

    public static String encodePretty(List<Quote> quotes) {
        try {
            return buildEnvelope(quotes).toString(2);
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
        JSONArray quotesArray = new JSONArray();
        for (Quote quote : quotes) {
            JSONObject jsonQuote = new JSONObject();
            jsonQuote.put("id", quote.getId());
            jsonQuote.put("author", quote.getAuthor());
            jsonQuote.put("quoteText", quote.getQuoteText());
            jsonQuote.put("source", quote.getSource());
            jsonQuote.put("category", quote.getCategory());
            jsonQuote.put("isFavorite", quote.isFavorite());
            jsonQuote.put("favoritedAt", quote.getFavoritedAt());
            jsonQuote.put("lastShown", quote.getLastShown());
            jsonQuote.put("timesShown", quote.getTimesShown());
            quotesArray.put(jsonQuote);
        }
        JSONObject envelope = new JSONObject();
        envelope.put("version", VERSION);
        envelope.put("quotes", quotesArray);
        return envelope;
    }

    private static List<Quote> parseQuotesArray(JSONArray jsonArray) {
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
                String category = jsonQuote.optString("category", "");
                boolean isFavorite = jsonQuote.optBoolean("isFavorite", false);
                long favoritedAt = jsonQuote.optLong("favoritedAt", 0);
                long lastShown = jsonQuote.optLong("lastShown", 0);
                int timesShown = jsonQuote.optInt("timesShown", 0);

                Quote quote = new Quote(id, author, quoteText, source);
                quote.setCategory(category);
                quote.setFavorite(isFavorite);
                quote.setFavoritedAt(favoritedAt);
                quote.setLastShown(lastShown);
                quote.setTimesShown(timesShown);
                quotes.add(quote);
            } catch (JSONException e) {
                Log.w(TAG, "Skipping quote at index " + i + ": " + e.getMessage());
            }
        }
        return quotes;
    }
}
