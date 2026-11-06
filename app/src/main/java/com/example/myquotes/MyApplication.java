package com.example.myquotes;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.myquotes.backup.LocalBackup;
import com.example.myquotes.drive.DriveBackup;
import com.example.myquotes.embedding.AssetEmbedder;
import com.example.myquotes.embedding.TextEmbeddingModel;
import com.example.myquotes.notifications.QuoteNotifications;

import java.io.File;
import java.util.Random;
import java.util.concurrent.Executors;

public class MyApplication extends Application {
    private static MyApplication instance;
    private QuoteStore quoteStore;
    private QuoteCollection quoteCollection;
    private Hashtags hashtags;
    private QuoteEmbeddings quoteEmbeddings;
    private Random shuffleRandom = new Random();

    private static final String PREFS_NAME = "AppSettings";
    private static final String KEY_THEME_MODE = "theme_mode";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        applyTheme();
        quoteStore = new SharedPreferencesQuoteStore(this);
        quoteCollection = new QuoteCollection(quoteStore);
        quoteCollection.loadFromStore();
        hashtags = new Hashtags(quoteCollection);
        Handler main = new Handler(Looper.getMainLooper());
        // The similar-quotes model chosen in ADR-006.
        quoteEmbeddings = new QuoteEmbeddings(quoteCollection,
                new AssetEmbedder(getAssets(), TextEmbeddingModel.PARAPHRASE_MULTILINGUAL_MINILM_L12),
                new QuoteEmbeddingsFile(new File(getFilesDir(), "quote-embeddings.bin")),
                Executors.newSingleThreadExecutor(r -> new Thread(() -> {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                    r.run();
                }, "QuoteEmbeddings")), main::post);
        removeLegacyCategoryList();
        QuoteNotifications.initialize(this);
        LocalBackup.initialize(this);
        DriveBackup.initialize(this);
    }

    public static MyApplication getInstance() {
        return instance;
    }

    public QuoteStore getQuoteStore() {
        return quoteStore;
    }

    public QuoteCollection getQuoteCollection() {
        return quoteCollection;
    }

    public Hashtags getHashtags() {
        return hashtags;
    }

    public QuoteEmbeddings getQuoteEmbeddings() {
        return quoteEmbeddings;
    }

    // Before #49 the user could keep category names without quotes; tags exist only in use.
    private void removeLegacyCategoryList() {
        SharedPreferences legacy = getSharedPreferences("CategoryPrefs", MODE_PRIVATE);
        if (legacy.contains("saved_categories")) {
            legacy.edit().remove("saved_categories").apply();
        }
    }

    /** Shuffles each new Reading Session's deck. */
    public Random getShuffleRandom() {
        return shuffleRandom;
    }

    /** For the README screenshot test: a seeded Random gives the same deck on every run. */
    public void setShuffleRandom(Random random) {
        shuffleRandom = random;
    }

    public void applyTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_NO);
        AppCompatDelegate.setDefaultNightMode(themeMode);
    }

    public void setThemeMode(int mode) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        // commit, not apply: the README screenshot run restores the setting just before its
        // process ends, which would drop an asynchronous write.
        prefs.edit().putInt(KEY_THEME_MODE, mode).commit();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public int getThemeMode() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_NO);
    }
}
