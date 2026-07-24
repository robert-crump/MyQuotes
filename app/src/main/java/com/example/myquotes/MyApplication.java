package com.example.myquotes;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.myquotes.backup.LocalBackup;
import com.example.myquotes.notifications.QuoteNotifications;

public class MyApplication extends Application {
    private static MyApplication instance;
    private QuoteCollection quoteCollection;

    private static final String PREFS_NAME = "AppSettings";
    private static final String KEY_THEME_MODE = "theme_mode";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        applyTheme();
        quoteCollection = new QuoteCollection(this);
        QuoteNotifications.initialize(this);
        LocalBackup.initialize(this);
    }

    public static MyApplication getInstance() {
        return instance;
    }

    public QuoteCollection getQuoteCollection() {
        return quoteCollection;
    }

    public void applyTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_NO);
        AppCompatDelegate.setDefaultNightMode(themeMode);
    }

    public void setThemeMode(int mode) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public int getThemeMode() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_NO);
    }
}
