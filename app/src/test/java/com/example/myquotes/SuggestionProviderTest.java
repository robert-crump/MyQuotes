package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class SuggestionProviderTest {
    private final SuggestionProvider provider = new SuggestionProvider();

    private static Quote q(String author, String source) {
        return new Quote(1, author, "text", source);
    }

    @Test
    public void authorTiersExactThenPrefixThenContains() {
        List<Quote> quotes = Arrays.asList(
                q("Old Sen", null), q("Seneca", null), q("Sen", null));
        assertEquals(Arrays.asList("Sen", "Seneca", "Old Sen"),
                provider.getAuthorSuggestions(quotes, "sen"));
    }

    @Test
    public void authorMatchIsCaseInsensitive() {
        List<Quote> quotes = Collections.singletonList(q("Seneca", null));
        assertEquals(Collections.singletonList("Seneca"),
                provider.getAuthorSuggestions(quotes, "SENE"));
    }

    @Test
    public void authorDuplicatesCollapseIgnoringCase() {
        List<Quote> quotes = Arrays.asList(q("Seneca", null), q("seneca", null), q("Seneca", null));
        assertEquals(Collections.singletonList("Seneca"),
                provider.getAuthorSuggestions(quotes, "sen"));
    }

    @Test
    public void authorWithinTierSortsByCountThenName() {
        List<Quote> quotes = Arrays.asList(
                q("Sena", null), q("Senb", null), q("Senb", null), q("Senc", null));
        assertEquals(Arrays.asList("Senb", "Sena", "Senc"),
                provider.getAuthorSuggestions(quotes, "sen"));
    }

    @Test
    public void authorNoMatchOrEmptyAuthorsYieldNothing() {
        List<Quote> quotes = Arrays.asList(q("Seneca", null), q(null, null));
        assertTrue(provider.getAuthorSuggestions(quotes, "zzz").isEmpty());
        assertTrue(provider.getAuthorSuggestions(Collections.singletonList(q("", null)), "a").isEmpty());
    }

    @Test
    public void sourcesSplitByAuthorContextAndSortAlphabetically() {
        List<Quote> quotes = Arrays.asList(
                q("Seneca", "Letters B"), q("Seneca", "letters a"),
                q("Plato", "Letters C"), q("Plato", "Letters B"));
        SourceSuggestions s = provider.getSourceSuggestions(quotes, "letters", "seneca");
        assertEquals(2, s.authorSources.size());
        assertEquals("letters a", s.authorSources.get(0));
        assertEquals("Letters B", s.authorSources.get(1));
        assertEquals(Collections.singletonList("Letters C"), s.otherSources);
        assertTrue(s.hasSplit());
    }

    @Test
    public void sourcesWithEmptyAuthorContextAreAllOthers() {
        List<Quote> quotes = Arrays.asList(q("Seneca", "Beta"), q("Plato", "Alpha"), q("X", ""));
        SourceSuggestions s = provider.getSourceSuggestions(quotes, "a", "");
        assertTrue(s.authorSources.isEmpty());
        assertEquals(Arrays.asList("Alpha", "Beta"), s.otherSources);
        assertFalse(s.hasSplit());
    }

    @Test
    public void sourcesDuplicatesCollapseAndOnlyMatchingKept() {
        List<Quote> quotes = Arrays.asList(q("A", "Book"), q("B", "book"), q("C", "Other"));
        SourceSuggestions s = provider.getSourceSuggestions(quotes, "boo", "");
        assertEquals(Collections.singletonList("Book"), s.otherSources);
    }

    @Test
    public void noSplitWhenAuthorHasNoMatchingSourceOrNoOthers() {
        List<Quote> quotes = Arrays.asList(q("Seneca", "Letters"), q("Plato", "Republic"));
        assertFalse(provider.getSourceSuggestions(quotes, "rep", "Seneca").hasSplit());
        SourceSuggestions onlyAuthor = provider.getSourceSuggestions(quotes, "let", "Seneca");
        assertEquals(Collections.singletonList("Letters"), onlyAuthor.authorSources);
        assertTrue(onlyAuthor.otherSources.isEmpty());
        assertFalse(onlyAuthor.hasSplit());
    }

    private static Quote qc(String author, String source, String tags) {
        Quote quote = new Quote(1, author, "text", source);
        quote.setTags(tags.isEmpty() ? Collections.emptyList() : Arrays.asList(tags.split(" ")));
        return quote;
    }

    private static List<String> values(List<SuggestionProvider.FieldSuggestion> suggestions) {
        List<String> values = new ArrayList<>();
        for (SuggestionProvider.FieldSuggestion s : suggestions) values.add(s.value + ":" + s.count);
        return values;
    }

    @Test
    public void fieldSuggestionsMatchTheStartOfAnyWord() {
        List<Quote> quotes = Arrays.asList(
                qc("A", "Thinking, Fast and Slow", ""), qc("B", "Rethinking", ""));
        assertEquals(Collections.singletonList("Thinking, Fast and Slow:1"), values(
                provider.getFieldSuggestions(quotes, QuoteQuery.Field.SOURCE, "think", 6)));
        assertEquals(Collections.singletonList("Thinking, Fast and Slow:1"), values(
                provider.getFieldSuggestions(quotes, QuoteQuery.Field.SOURCE, "FAST", 6)));
        assertEquals(Collections.singletonList("Thinking, Fast and Slow:1"), values(
                provider.getFieldSuggestions(quotes, QuoteQuery.Field.SOURCE, "thinking, fa", 6)));
    }

    @Test
    public void fieldSuggestionsRankByCountThenNameAndRespectTheLimit() {
        List<Quote> quotes = Arrays.asList(
                qc("Seneca", "", ""), qc("seneca", "", ""), qc("Amartya Sen", "", ""),
                qc("Sendak", "", ""), qc("Plato", "", ""));
        assertEquals(Arrays.asList("Seneca:2", "Amartya Sen:1", "Sendak:1"), values(
                provider.getFieldSuggestions(quotes, QuoteQuery.Field.AUTHOR, "sen", 6)));
        assertEquals(Arrays.asList("Seneca:2", "Amartya Sen:1"), values(
                provider.getFieldSuggestions(quotes, QuoteQuery.Field.AUTHOR, "sen", 2)));
    }

    @Test
    public void fieldSuggestionsCoverHashtagsButNotQuoteTextOrEmptyInput() {
        List<Quote> quotes = Arrays.asList(qc("A", "", "Stoicism Zen"), qc("B", "", ""));
        assertEquals(Collections.singletonList("Stoicism:1"), values(
                provider.getFieldSuggestions(quotes, QuoteQuery.Field.HASHTAGS, "sto", 6)));
        assertTrue(provider.getFieldSuggestions(quotes, QuoteQuery.Field.QUOTE_TEXT, "tex", 6).isEmpty());
        assertTrue(provider.getFieldSuggestions(quotes, QuoteQuery.Field.HASHTAGS, " ", 6).isEmpty());
    }

    @Test
    public void tagSuggestionsStartWithTheInputIgnoringHashAndRankByUsage() {
        List<Quote> quotes = Arrays.asList(
                qc("A", "", "Love Life"), qc("B", "", "Life Wisdom"), qc("C", "", "Life Love"),
                qc("D", "", "Light"), qc("E", "", "Belief"));
        assertEquals(Arrays.asList("Life:3", "Light:1"), values(provider.getTagSuggestions(quotes, "#li", 6)));
        assertEquals(Arrays.asList("Life:3", "Love:2"), values(provider.getTagSuggestions(quotes, "L", 2)));
    }

    @Test
    public void tagSuggestionsForEmptyInputAreTheMostUsed() {
        List<Quote> quotes = Arrays.asList(
                qc("A", "", "Love Life"), qc("B", "", "Life Wisdom"), qc("C", "", "Life Love"));
        assertEquals(Arrays.asList("Life:3", "Love:2", "Wisdom:1"),
                values(provider.getTagSuggestions(quotes, "", 6)));
    }
}
