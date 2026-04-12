package com.example.myquotes;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class QuotePreferences {
    private static final String TAG = "QuotePreferences";
    private static final String PREFS_NAME = "QuotePrefs";
    private static final String KEY_LAST_SYNC = "last_sync_time";
    private static final String KEY_QUOTES_JSON = "quotes_json";
    private static final String KEY_FIRST_LAUNCH = "is_first_launch";
    private static final String KEY_INITIAL_CSV_LOADED = "initial_csv_loaded";

    private final SharedPreferences prefs;

    public QuotePreferences(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public long getLastSyncTime() {
        return prefs.getLong(KEY_LAST_SYNC, 0);
    }

    public void setLastSyncTime(long timestamp) {
        prefs.edit().putLong(KEY_LAST_SYNC, timestamp).apply();
    }

    public boolean isFirstLaunch() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }

    public void setFirstLaunchComplete() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
    }

    public boolean isInitialCsvLoaded() {
        return prefs.getBoolean(KEY_INITIAL_CSV_LOADED, false);
    }

    public void setInitialCsvLoaded(boolean loaded) {
        prefs.edit().putBoolean(KEY_INITIAL_CSV_LOADED, loaded).apply();
    }

    public void saveQuotes(List<Quote> quotes) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (Quote quote : quotes) {
                JSONObject jsonQuote = new JSONObject();
                jsonQuote.put("id", quote.getId());
                jsonQuote.put("author", quote.getAuthor());
                jsonQuote.put("quoteText", quote.getQuoteText());
                jsonQuote.put("source", quote.getSource());
                jsonQuote.put("category", quote.getCategory());
                jsonQuote.put("rating", quote.getRating());
                jsonQuote.put("isFavorite", quote.isFavorite());
                jsonQuote.put("favoritedAt", quote.getFavoritedAt());
                jsonQuote.put("lastShown", quote.getLastShown());
                jsonQuote.put("timesShown", quote.getTimesShown());
                jsonArray.put(jsonQuote);
            }

            prefs.edit().putString(KEY_QUOTES_JSON, jsonArray.toString()).apply();
            Log.d(TAG, "Saved " + quotes.size() + " quotes to preferences");
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save quotes", e);
        }
    }

    public List<Quote> loadQuotes() {
        String jsonString = prefs.getString(KEY_QUOTES_JSON, null);
        if (jsonString == null) {
            Log.d(TAG, "No quotes found in preferences");
            return null;
        }

        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            List<Quote> quotes = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonQuote = jsonArray.getJSONObject(i);

                int id = jsonQuote.getInt("id");
                String author = jsonQuote.optString("author", "");
                String quoteText = jsonQuote.optString("quoteText", "");
                String source = jsonQuote.optString("source", "");
                String category = jsonQuote.optString("category", "");
                int rating = jsonQuote.optInt("rating", 0);
                boolean isFavorite = jsonQuote.optBoolean("isFavorite", false);
                long favoritedAt = jsonQuote.optLong("favoritedAt", 0);
                long lastShown = jsonQuote.optLong("lastShown", 0);
                int timesShown = jsonQuote.optInt("timesShown", 0);

                Quote quote = new Quote(id, author, quoteText, source);
                quote.setCategory(category);
                quote.setRating(rating);
                quote.setFavorite(isFavorite);
                quote.setFavoritedAt(favoritedAt);
                quote.setLastShown(lastShown);
                quote.setTimesShown(timesShown);

                quotes.add(quote);
            }

            Log.d(TAG, "Loaded " + quotes.size() + " quotes from preferences");
            return quotes;
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load quotes", e);
            return null;
        }
    }

    public boolean hasPersistedQuotes() {
        return prefs.contains(KEY_QUOTES_JSON);
    }
}