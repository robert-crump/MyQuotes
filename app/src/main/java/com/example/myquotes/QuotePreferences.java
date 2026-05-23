package com.example.myquotes;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

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
        prefs.edit().putString(KEY_QUOTES_JSON, QuoteCodec.encode(quotes)).apply();
        Log.d(TAG, "Saved " + quotes.size() + " quotes to preferences");
    }

    public List<Quote> loadQuotes() {
        String jsonString = prefs.getString(KEY_QUOTES_JSON, null);
        if (jsonString == null) {
            Log.d(TAG, "No quotes found in preferences");
            return null;
        }

        try {
            List<Quote> quotes = QuoteCodec.decode(jsonString);
            Log.d(TAG, "Loaded " + quotes.size() + " quotes from preferences");
            return quotes;
        } catch (QuoteCodecException e) {
            Log.e(TAG, "Failed to load quotes", e);
            return null;
        }
    }

    public boolean hasPersistedQuotes() {
        return prefs.contains(KEY_QUOTES_JSON);
    }
}