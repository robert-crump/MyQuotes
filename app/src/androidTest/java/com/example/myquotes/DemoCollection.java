package com.example.myquotes;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The public-domain demo quotes the README screenshots are taken from
 * ({@code androidTest/assets/demo_quotes.json}, a v3 Backup Document with hashtags).
 */
final class DemoCollection {
    private static final String ASSET = "demo_quotes.json";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private DemoCollection() {}

    /**
     * The demo quotes with every timestamp shifted by the same amount so the newest was added
     * two days before {@code now}; the Statistics chart covers the 12 months up to now.
     */
    static List<Quote> load(long now) throws IOException, QuoteCodecException {
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        List<Quote> quotes;
        try (InputStream in = testContext.getAssets().open(ASSET)) {
            quotes = BackupDocument.decode(readUtf8(in)).quotes;
        }
        long newest = 0;
        for (Quote q : quotes) newest = Math.max(newest, q.getAddedAt());
        long offset = now - 2 * DAY_MS - newest;
        for (Quote q : quotes) {
            q.setAddedAt(shift(q.getAddedAt(), offset));
            q.setFavoritedAt(shift(q.getFavoritedAt(), offset));
            q.setLastShown(shift(q.getLastShown(), offset));
        }
        return quotes;
    }

    /**
     * Replaces the Quote Collection (and with it the Hashtag set) with the demo quotes and marks
     * the deck as swiped, so the nudge doesn't play. Call on the main thread.
     */
    static void install(Context context, List<Quote> quotes) {
        MyApplication.getInstance().getQuoteCollection().setList(new ArrayList<>(quotes));
        SwipeHint.markSwiped(context);
    }

    static Quote find(List<Quote> quotes, String textStart) {
        for (Quote q : quotes) {
            if (q.getQuoteText().startsWith(textStart)) return q;
        }
        throw new AssertionError("No demo quote starts with \"" + textStart + "\"");
    }

    private static long shift(long timestamp, long offset) {
        return timestamp > 0 ? timestamp + offset : 0;
    }

    private static String readUtf8(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
