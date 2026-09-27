package com.example.myquotes.notifications;

import com.example.myquotes.Quote;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Chooses the daily-notification quote (#37). Pure: clock and randomness are passed in.
 * <ul>
 *   <li><b>New pool</b>: added in-app ({@code addedAt > 0}), never notified, and at least
 *       {@link #NEW_GRACE_MILLIS} old. Exempt from the cooldown.</li>
 *   <li><b>Regular pool</b>: every other quote whose last notification and last in-app view are
 *       both at least {@link #COOLDOWN_MILLIS} ago. New quotes still inside the grace period are
 *       in neither pool.</li>
 * </ul>
 * With a non-empty new pool, it is drawn from with probability {@link #NEW_POOL_CHANCE};
 * otherwise the regular pool is used. An empty chosen pool falls through to the other one; with
 * both empty, the quote least recently notified or viewed wins.
 */
final class QuoteOfTheDayPicker {
    static final double NEW_POOL_CHANCE = 0.25;
    static final long COOLDOWN_MILLIS = 30L * 24 * 60 * 60 * 1000;
    static final long NEW_GRACE_MILLIS = 24L * 60 * 60 * 1000;

    private QuoteOfTheDayPicker() {}

    /** Null only if {@code quotes} is empty. */
    static Quote pick(List<Quote> quotes, NotificationHistory history, long nowMillis, Random random) {
        List<Quote> newPool = new ArrayList<>();
        List<Quote> regularPool = new ArrayList<>();
        List<Quote> settled = new ArrayList<>();  // everything outside the grace period

        for (Quote quote : quotes) {
            NotificationHistory.Entry entry = history.entryFor(quote);
            boolean neverNotified = entry == null || entry.notifiedCount == 0;
            boolean isNew = quote.getAddedAt() > 0 && neverNotified;
            if (isNew && nowMillis - quote.getAddedAt() < NEW_GRACE_MILLIS) continue;
            settled.add(quote);
            if (isNew) {
                newPool.add(quote);
            } else if (nowMillis - lastSeen(quote, entry) >= COOLDOWN_MILLIS) {
                regularPool.add(quote);
            }
        }

        if (!newPool.isEmpty() && (regularPool.isEmpty() || random.nextDouble() < NEW_POOL_CHANCE)) {
            return newPool.get(random.nextInt(newPool.size()));
        }
        if (!regularPool.isEmpty()) {
            return regularPool.get(random.nextInt(regularPool.size()));
        }
        return leastRecentlySeen(settled.isEmpty() ? quotes : settled, history);
    }

    private static long lastSeen(Quote quote, NotificationHistory.Entry entry) {
        long lastNotifiedAt = entry == null ? 0 : entry.lastNotifiedAt;
        return Math.max(lastNotifiedAt, quote.getLastShown());
    }

    private static Quote leastRecentlySeen(List<Quote> quotes, NotificationHistory history) {
        Quote best = null;
        long bestSeen = Long.MAX_VALUE;
        for (Quote quote : quotes) {
            long seen = lastSeen(quote, history.entryFor(quote));
            if (seen < bestSeen) {
                best = quote;
                bestSeen = seen;
            }
        }
        return best;
    }
}
