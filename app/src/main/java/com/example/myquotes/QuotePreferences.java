package com.example.myquotes;

import android.content.Context;
import android.content.SharedPreferences;

public class QuotePreferences {
    private static final String PREFS_NAME = "QuotePrefs";
    private static final String KEY_FIRST_LAUNCH = "is_first_launch";

    private final SharedPreferences prefs;

    public QuotePreferences(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isFirstLaunch() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }

    public void setFirstLaunchComplete() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
    }
}
