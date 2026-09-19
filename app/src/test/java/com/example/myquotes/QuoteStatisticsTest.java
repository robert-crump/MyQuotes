package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
