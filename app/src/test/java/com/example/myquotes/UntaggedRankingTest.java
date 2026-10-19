package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class UntaggedRankingTest {
    private final List<Quote> quotes = new ArrayList<>();

    private Quote add(String author, String text, long addedAt, String... tags) {
        Quote q = new Quote(quotes.size() + 1, author, text, "");
        q.setTags(Arrays.asList(tags));
        q.setAddedAt(addedAt);
        quotes.add(q);
        return q;
    }

    private void addTagged(int count, String text, String tag) {
        for (int i = 0; i < count; i++) add("Author " + quotes.size(), text, 0, tag);
    }

    private static HashtagSuggester.Suggestion suggestion(String tag, int withTag, int total) {
        return new HashtagSuggester.Suggestion(tag, new HashtagSuggester.Evidence("word", withTag, total));
    }

    private static UntaggedRanking.Entry entry(Quote quote, HashtagSuggester.Suggestion... suggestions) {
        return new UntaggedRanking.Entry(quote, Arrays.asList(suggestions));
    }

    private List<Quote> ranked(List<Quote> candidates) {
        List<Quote> order = new ArrayList<>();
        for (UntaggedRanking.Entry e : UntaggedRanking.rank(candidates, HashtagSuggester.fit(quotes))) {
            order.add(e.quote);
        }
        return order;
    }

    @Test
    public void theStrongestTopSuggestionComesFirstAndQuotesWithoutSuggestionsLastNewestFirst() {
        addTagged(5, "forest walk", "Nature");
        addTagged(5, "heart rose", "Love");
        add("E", "forest lake", 0); // not a candidate, but it counts
        Quote forest = add("A", "forest gold", 4);
        Quote heart = add("B", "heart silver", 1);
        Quote old = add("C", "money", 2);
        Quote recent = add("D", "office", 3);
        // heart 5/5 beats forest 5/6.
        assertEquals(Arrays.asList(heart, forest, recent, old),
                ranked(Arrays.asList(old, forest, recent, heart)));
    }

    @Test
    public void tiesGoToTheSecondSuggestionThenNewestFirst() {
        Quote one = new Quote(1, "A", "x", "");
        Quote two = new Quote(2, "B", "y", "");
        Quote three = new Quote(3, "C", "z", "");
        List<UntaggedRanking.Entry> entries = new ArrayList<>(Arrays.asList(
                entry(one, suggestion("Love", 5, 5)),
                entry(two, suggestion("Love", 5, 5), suggestion("Life", 4, 8)),
                entry(three, suggestion("Love", 5, 5))));
        entries.sort(UntaggedRanking.ORDER);
        // two has a 2nd suggestion; one and three tie, so the newer id first.
        assertEquals(Arrays.asList(two, three, one),
                Arrays.asList(entries.get(0).quote, entries.get(1).quote, entries.get(2).quote));
    }

    @Test
    public void nextIsTheBestUntaggedQuoteMatchingTheQueryAndNotSkipped() {
        addTagged(5, "heart rose", "Love");
        Quote best = add("Rumi", "heart one", 1);
        Quote second = add("Rumi", "heart two", 1);
        Quote other = add("Seneca", "heart three", 9);
        HashtagSuggester model = HashtagSuggester.fit(quotes);
        QuoteQuery rumi = QuoteQuery.forField(QuoteQuery.Field.AUTHOR, "Rumi").withUntagged();

        // All three are heart 5/7: newest first, then the higher id.
        assertEquals(second, UntaggedRanking.next(quotes, rumi, model, Collections.emptySet()));
        assertEquals(other, UntaggedRanking.next(quotes, QuoteQuery.untagged(), model, Collections.emptySet()));
        assertEquals(best, UntaggedRanking.next(quotes, rumi, model, Collections.singleton(second.getId())));
        assertNull(UntaggedRanking.next(quotes, rumi, model, new HashSet<>(Arrays.asList(best.getId(), second.getId()))));
    }

    @Test
    public void remainingCountsTheUntaggedQuotesNotSkipped() {
        addTagged(5, "heart rose", "Love");
        Quote a = add("A", "one", 1);
        add("B", "two", 2);
        assertEquals(2, UntaggedRanking.remaining(quotes, QuoteQuery.untagged(), Collections.emptySet()));
        assertEquals(1, UntaggedRanking.remaining(quotes, QuoteQuery.untagged(), Collections.singleton(a.getId())));
    }
}
