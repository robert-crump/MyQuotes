package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class QuoteStatisticsTest {

    private static Quote q(String author, String source, String category, boolean fav) {
        Quote quote = new Quote(1, author, "text", source);
        quote.setCategory(category);
        quote.setFavorite(fav);
        return quote;
    }

    private static List<String> names(List<StatItem> items) {
        List<String> out = new ArrayList<>();
        for (StatItem i : items) out.add(i.name + ":" + i.count);
        return out;
    }

    @Test
    public void countsAndFavorites() {
        QuoteStatistics s = QuoteStatistics.of(Arrays.asList(
                q("A", "S1", "Life", true), q("A", "S1", "Life", false), q("B", "S2", "Work", true)));
        assertEquals(3, s.total);
        assertEquals(2, s.favoriteCount);
        assertEquals(Arrays.asList("A:2", "B:1"), names(s.topAuthors));
        assertEquals(Arrays.asList("S1:2", "S2:1"), names(s.topSources));
        assertEquals(Arrays.asList("Life:2", "Work:1"), names(s.categories));
    }

    @Test
    public void topListsLimitedToTen_categoriesNot() {
        List<Quote> quotes = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            quotes.add(q("author" + i, "src" + i, "cat" + i, false));
        }
        QuoteStatistics s = QuoteStatistics.of(quotes);
        assertEquals(10, s.topAuthors.size());
        assertEquals(10, s.topSources.size());
        assertEquals(12, s.categories.size());
    }

    @Test
    public void emptySourceExcluded() {
        QuoteStatistics s = QuoteStatistics.of(Arrays.asList(q("A", "", "", false), q("B", "S", "", false)));
        assertEquals(Collections.singletonList("S:1"), names(s.topSources));
    }

    @Test
    public void emptyCategoryCountedAsWithoutCategory() {
        QuoteStatistics s = QuoteStatistics.of(Arrays.asList(q("A", "", "", false), q("B", "", "X", false)));
        assertEquals(1, s.withoutCategoryCount);
        assertEquals(Collections.singletonList("X:1"), names(s.categories));
    }

    @Test
    public void tiesOrderedByNameAscending() {
        QuoteStatistics s = QuoteStatistics.of(Arrays.asList(
                q("Zed", "", "", false), q("Amy", "", "", false), q("Bob", "", "", false), q("Bob", "", "", false)));
        assertEquals(Arrays.asList("Bob:2", "Amy:1", "Zed:1"), names(s.topAuthors));
    }

    @Test
    public void emptyListYieldsZerosAndEmptyLists() {
        QuoteStatistics s = QuoteStatistics.of(Collections.emptyList());
        assertEquals(0, s.total);
        assertEquals(0, s.favoriteCount);
        assertEquals(0, s.withoutCategoryCount);
        assertTrue(s.topAuthors.isEmpty() && s.topSources.isEmpty() && s.categories.isEmpty());
    }

    private static final ZoneId ZONE = ZoneOffset.UTC;
    // 2026-09-28 12:00 UTC: window is Oct 2025 .. Sep 2026.
    private static final long NOW = at(2026, 9, 28, 12);

    private static long at(int year, int month, int day, int hour) {
        return LocalDateTime.of(year, month, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static Quote added(long addedAt) {
        Quote quote = q("A", "", "", false);
        quote.setAddedAt(addedAt);
        return quote;
    }

    private static List<Integer> counts(QuoteStatistics s) {
        List<Integer> out = new ArrayList<>();
        for (QuoteStatistics.MonthCount m : s.addedPerMonth) out.add(m.count);
        return out;
    }

    @Test
    public void addedPerMonthCoversLastTwelveMonthsOldestFirst() {
        QuoteStatistics s = QuoteStatistics.of(Collections.emptyList(), NOW, ZONE);
        assertEquals(12, s.addedPerMonth.size());
        assertEquals(YearMonth.of(2025, 10), s.addedPerMonth.get(0).month);
        assertEquals(YearMonth.of(2026, 9), s.addedPerMonth.get(11).month);
        assertEquals(Collections.nCopies(12, 0), counts(s));
    }

    @Test
    public void addedPerMonthBucketsByCalendarMonthAtWindowEdges() {
        QuoteStatistics s = QuoteStatistics.of(Arrays.asList(
                added(at(2025, 10, 1, 0)),     // first day of the window
                added(at(2025, 9, 30, 23)),    // just before the window
                added(at(2026, 9, 1, 0)),      // current month
                added(at(2026, 9, 28, 11)),    // current month
                added(at(2026, 10, 1, 0)),     // future (clock skew): ignored
                added(at(2026, 3, 15, 12))), NOW, ZONE);
        assertEquals(Arrays.asList(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 2), counts(s));
        assertEquals(0, s.unknownAddedCount);
    }

    @Test
    public void addedPerMonthUsesTheGivenZone() {
        // 2026-08-31 23:30 UTC is already September in UTC+2.
        long lateAugustUtc = at(2026, 8, 31, 23) + 30 * 60_000L;
        QuoteStatistics s = QuoteStatistics.of(Collections.singletonList(added(lateAugustUtc)),
                NOW, ZoneOffset.ofHours(2));
        assertEquals(1, (int) counts(s).get(11));
    }

    @Test
    public void unknownAddedCountedSeparately() {
        QuoteStatistics s = QuoteStatistics.of(Arrays.asList(added(0), added(0), added(at(2026, 9, 2, 8))), NOW, ZONE);
        assertEquals(2, s.unknownAddedCount);
        assertEquals(1, (int) counts(s).get(11));
    }
}
