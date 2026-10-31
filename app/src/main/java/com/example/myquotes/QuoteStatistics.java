package com.example.myquotes;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable aggregate over a quote list. Pure: no Android dependencies. */
public final class QuoteStatistics {
    static final int MONTHS = 12;

    /** Quotes added in one calendar month. */
    public static final class MonthCount {
        public final YearMonth month;
        public final int count;

        MonthCount(YearMonth month, int count) {
            this.month = month;
            this.count = count;
        }
    }

    public final int total;
    public final int favoriteCount;
    /** Every author and source, most quotes first; ties by name. */
    public final List<StatItem> authors;
    public final List<StatItem> sources;
    /** Every hashtag; a quote counts once for each of its tags. */
    public final List<StatItem> hashtags;
    public final int withoutHashtagsCount;
    /** The last {@link #MONTHS} calendar months, oldest first, ending with the current one. */
    public final List<MonthCount> addedPerMonth;
    /** Quotes with {@code addedAt == 0} (added before the field existed). */
    public final int unknownAddedCount;

    private QuoteStatistics(int total, int favoriteCount, List<StatItem> authors,
                            List<StatItem> sources, List<StatItem> hashtags,
                            int withoutHashtagsCount, List<MonthCount> addedPerMonth,
                            int unknownAddedCount) {
        this.total = total;
        this.favoriteCount = favoriteCount;
        this.authors = authors;
        this.sources = sources;
        this.hashtags = hashtags;
        this.withoutHashtagsCount = withoutHashtagsCount;
        this.addedPerMonth = addedPerMonth;
        this.unknownAddedCount = unknownAddedCount;
    }

    public static QuoteStatistics of(List<Quote> quotes) {
        return of(quotes, System.currentTimeMillis(), ZoneId.systemDefault());
    }

    static QuoteStatistics of(List<Quote> quotes, long nowMillis, ZoneId zone) {
        if (quotes == null) quotes = Collections.emptyList();
        int favorites = 0;
        int noHashtags = 0;
        int unknownAdded = 0;
        YearMonth current = YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(zone));
        YearMonth first = current.minusMonths(MONTHS - 1);
        int[] perMonth = new int[MONTHS];
        Map<String, Integer> authors = new HashMap<>();
        Map<String, Integer> sources = new HashMap<>();
        Map<String, Integer> hashtags = new HashMap<>();
        for (Quote q : quotes) {
            if (q.isFavorite()) favorites++;
            authors.merge(q.getAuthor(), 1, Integer::sum);
            if (!q.getSource().isEmpty()) sources.merge(q.getSource(), 1, Integer::sum);
            if (q.getTags().isEmpty()) noHashtags++;
            for (String tag : q.getTags()) hashtags.merge(tag, 1, Integer::sum);
            if (q.getAddedAt() == 0) {
                unknownAdded++;
            } else {
                YearMonth added = YearMonth.from(Instant.ofEpochMilli(q.getAddedAt()).atZone(zone));
                if (!added.isBefore(first) && !added.isAfter(current)) {
                    perMonth[(int) first.until(added, ChronoUnit.MONTHS)]++;
                }
            }
        }
        List<MonthCount> months = new ArrayList<>();
        for (int i = 0; i < MONTHS; i++) {
            months.add(new MonthCount(first.plusMonths(i), perMonth[i]));
        }
        return new QuoteStatistics(quotes.size(), favorites,
                rank(authors), rank(sources), rank(hashtags), noHashtags,
                Collections.unmodifiableList(months), unknownAdded);
    }

    private static List<StatItem> rank(Map<String, Integer> counts) {
        List<StatItem> items = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            items.add(new StatItem(e.getKey(), e.getValue()));
        }
        items.sort(Comparator.<StatItem>comparingInt(i -> -i.count).thenComparing(i -> i.name));
        return Collections.unmodifiableList(items);
    }
}
