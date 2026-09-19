package com.example.myquotes;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class SharedPreferencesQuoteStore implements QuoteStore {
    private static final String TAG = "QuoteStore";
    private static final String PREFS_NAME = "QuotePrefs";
    private static final String KEY_QUOTES_JSON = "quotes_json";

    private final SharedPreferences prefs;

    public SharedPreferencesQuoteStore(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public List<Quote> load() {
        String json = prefs.getString(KEY_QUOTES_JSON, null);
        if (json == null) {
            Log.d(TAG, "No quotes found in preferences");
            return new ArrayList<>();
        }
        try {
            List<Quote> quotes = QuoteCodec.decode(json);
            Log.d(TAG, "Loaded " + quotes.size() + " quotes from preferences");
            return quotes;
        } catch (QuoteCodecException e) {
            Log.e(TAG, "Failed to load quotes", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void save(List<Quote> quotes) {
        prefs.edit().putString(KEY_QUOTES_JSON, QuoteCodec.encode(quotes)).apply();
        Log.d(TAG, "Saved " + quotes.size() + " quotes to preferences");
    }

    @Override
    public boolean hasStoredQuotes() {
        return prefs.contains(KEY_QUOTES_JSON);
    }
}
