package com.example.myquotes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable aggregate over a quote list. Pure: no Android dependencies. */
public final class QuoteStatistics {
    static final int TOP_LIMIT = 10;

    public final int total;
    public final int favoriteCount;
    public final List<StatItem> topAuthors;
    public final List<StatItem> topSources;
    public final List<StatItem> categories;
    public final int withoutCategoryCount;

    private QuoteStatistics(int total, int favoriteCount, List<StatItem> topAuthors,
                            List<StatItem> topSources, List<StatItem> categories,
                            int withoutCategoryCount) {
        this.total = total;
        this.favoriteCount = favoriteCount;
        this.topAuthors = topAuthors;
        this.topSources = topSources;
        this.categories = categories;
        this.withoutCategoryCount = withoutCategoryCount;
    }

    public static QuoteStatistics of(List<Quote> quotes) {
        if (quotes == null) quotes = Collections.emptyList();
        int favorites = 0;
        int noCategory = 0;
        Map<String, Integer> authors = new HashMap<>();
        Map<String, Integer> sources = new HashMap<>();
        Map<String, Integer> categories = new HashMap<>();
        for (Quote q : quotes) {
            if (q.isFavorite()) favorites++;
            authors.merge(q.getAuthor(), 1, Integer::sum);
            if (!q.getSource().isEmpty()) sources.merge(q.getSource(), 1, Integer::sum);
            if (q.getCategory().isEmpty()) noCategory++;
            else categories.merge(q.getCategory(), 1, Integer::sum);
        }
        return new QuoteStatistics(quotes.size(), favorites,
                rank(authors, TOP_LIMIT), rank(sources, TOP_LIMIT),
                rank(categories, Integer.MAX_VALUE), noCategory);
    }

    private static List<StatItem> rank(Map<String, Integer> counts, int limit) {
        List<StatItem> items = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            items.add(new StatItem(e.getKey(), e.getValue()));
        }
        items.sort(Comparator.<StatItem>comparingInt(i -> -i.count).thenComparing(i -> i.name));
        List<StatItem> top = new ArrayList<>(items.subList(0, Math.min(limit, items.size())));
        return Collections.unmodifiableList(top);
    }
}
