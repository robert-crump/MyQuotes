package com.example.myquotes.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.myquotes.Quote;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class QuoteOfTheDayPickerTest {
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long NOW = 1_700_000_000_000L;

    /** nextDouble() returns {@code roll}; nextInt(n) returns {@code index} (clamped to n-1). */
    private static final class FixedRandom extends Random {
        final double roll;
        final int index;

        FixedRandom(double roll, int index) {
            this.roll = roll;
            this.index = index;
        }

        @Override public double nextDouble() { return roll; }
        @Override public int nextInt(int bound) { return Math.min(index, bound - 1); }
    }

    private static final Random PICK_NEW = new FixedRandom(0.0, 0);
    private static final Random PICK_REGULAR = new FixedRandom(0.99, 0);

    private static Quote old(int id) {
        return new Quote(id, "A", "T" + id, "S");
    }

    private static Quote addedAt(int id, long addedAt) {
        Quote q = old(id);
        q.setAddedAt(addedAt);
        return q;
    }

    private static Quote shownAt(int id, long lastShown) {
        Quote q = old(id);
        q.setLastShown(lastShown);
        return q;
    }

    private static NotificationHistory notified(Quote quote, long at) {
        NotificationHistory history = new NotificationHistory();
        history.recordNotified(quote, at);
        return history;
    }

    @Test
    public void emptyCollectionPicksNothing() {
        assertNull(QuoteOfTheDayPicker.pick(new ArrayList<>(), new NotificationHistory(), NOW, PICK_NEW));
    }

    @Test
    public void newQuoteWinsWhenTheRollIsUnderTheChance() {
        Quote fresh = addedAt(2, NOW - 2 * DAY);
        List<Quote> quotes = Arrays.asList(old(1), fresh);

        assertEquals(fresh, QuoteOfTheDayPicker.pick(quotes, new NotificationHistory(), NOW,
                new FixedRandom(QuoteOfTheDayPicker.NEW_POOL_CHANCE - 0.01, 0)));
    }

    @Test
    public void regularPoolWinsWhenTheRollIsAtOrAboveTheChance() {
        Quote fresh = addedAt(2, NOW - 2 * DAY);
        List<Quote> quotes = Arrays.asList(old(1), fresh);

        assertEquals(old(1), QuoteOfTheDayPicker.pick(quotes, new NotificationHistory(), NOW,
                new FixedRandom(QuoteOfTheDayPicker.NEW_POOL_CHANCE, 0)));
    }

    @Test
    public void quotesWithoutAddedAtAreNeverNew() {
        // Pre-existing quotes (addedAt 0) all sit in the regular pool, even if never notified.
        List<Quote> quotes = Arrays.asList(old(1), old(2));
        assertEquals(old(2), QuoteOfTheDayPicker.pick(quotes, new NotificationHistory(), NOW,
                new FixedRandom(0.0, 1)));
    }

    @Test
    public void newQuoteInsideTheGracePeriodIsInNeitherPool() {
        Quote justAdded = addedAt(2, NOW - DAY + 1);
        List<Quote> quotes = Arrays.asList(old(1), justAdded);

        assertEquals(old(1), QuoteOfTheDayPicker.pick(quotes, new NotificationHistory(), NOW, PICK_NEW));
    }

    @Test
    public void newQuoteIgnoresTheCooldownFromInAppViews() {
        Quote fresh = addedAt(2, NOW - 2 * DAY);
        fresh.setLastShown(NOW - 60_000);
        List<Quote> quotes = Arrays.asList(old(1), fresh);

        assertEquals(fresh, QuoteOfTheDayPicker.pick(quotes, new NotificationHistory(), NOW, PICK_NEW));
    }

    @Test
    public void notifiedNewQuoteBecomesRegularAndCoolsDown() {
        Quote fresh = addedAt(2, NOW - 10 * DAY);
        NotificationHistory history = notified(fresh, NOW - DAY);
        List<Quote> quotes = Arrays.asList(old(1), fresh);

        assertEquals(old(1), QuoteOfTheDayPicker.pick(quotes, history, NOW, PICK_NEW));
    }

    @Test
    public void recentlyNotifiedQuoteIsOnCooldown() {
        Quote recent = old(1);
        NotificationHistory history = notified(recent, NOW - 29 * DAY);
        List<Quote> quotes = Arrays.asList(recent, old(2));

        assertEquals(old(2), QuoteOfTheDayPicker.pick(quotes, history, NOW, PICK_REGULAR));
    }

    @Test
    public void recentlyViewedQuoteIsOnCooldown() {
        List<Quote> quotes = Arrays.asList(shownAt(1, NOW - 29 * DAY), old(2));

        assertEquals(old(2), QuoteOfTheDayPicker.pick(quotes, new NotificationHistory(), NOW, PICK_REGULAR));
    }

    @Test
    public void cooldownEndsAfterThirtyDays() {
        List<Quote> quotes = Collections.singletonList(shownAt(1, NOW - QuoteOfTheDayPicker.COOLDOWN_MILLIS));
        NotificationHistory history = notified(quotes.get(0), NOW - QuoteOfTheDayPicker.COOLDOWN_MILLIS);

        assertEquals(old(1), QuoteOfTheDayPicker.pick(quotes, history, NOW, PICK_REGULAR));
    }

    @Test
    public void newPoolIsUsedWhenTheRegularPoolIsEmptyWhateverTheRoll() {
        Quote fresh = addedAt(2, NOW - 2 * DAY);
        List<Quote> quotes = Arrays.asList(shownAt(1, NOW - DAY), fresh);

        assertEquals(fresh, QuoteOfTheDayPicker.pick(quotes, new NotificationHistory(), NOW, PICK_REGULAR));
    }

    @Test
    public void allOnCooldownFallsBackToLeastRecentlySeen() {
        Quote viewedLongest = shownAt(1, NOW - 20 * DAY);
        Quote notifiedRecently = shownAt(2, NOW - 25 * DAY);
        NotificationHistory history = notified(notifiedRecently, NOW - DAY);
        List<Quote> quotes = Arrays.asList(shownAt(3, NOW - 5 * DAY), viewedLongest, notifiedRecently);

        assertEquals(viewedLongest, QuoteOfTheDayPicker.pick(quotes, history, NOW, PICK_REGULAR));
    }

    @Test
    public void fallbackPrefersSettledQuotesOverOnesInTheGracePeriod() {
        Quote justAdded = addedAt(2, NOW - 60_000);
        List<Quote> quotes = Arrays.asList(shownAt(1, NOW - DAY), justAdded);

        assertEquals(old(1), QuoteOfTheDayPicker.pick(quotes, new NotificationHistory(), NOW, PICK_NEW));
    }

    @Test
    public void onlyQuoteInTheGracePeriodIsStillPicked() {
        Quote justAdded = addedAt(1, NOW - 60_000);

        assertEquals(justAdded, QuoteOfTheDayPicker.pick(Collections.singletonList(justAdded),
                new NotificationHistory(), NOW, PICK_NEW));
    }

    @Test
    public void historyFromAReusedIdDoesNotCountAgainstTheNewQuote() {
        // Quote 2 was notified, deleted, and its id reused by a quote added later.
        NotificationHistory history = notified(old(2), NOW - 40 * DAY);
        Quote reused = addedAt(2, NOW - 3 * DAY);
        List<Quote> quotes = Arrays.asList(old(1), reused);

        assertEquals(reused, QuoteOfTheDayPicker.pick(quotes, history, NOW, PICK_NEW));
    }
}
