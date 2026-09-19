package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class QuoteQueryTest {
    private static Quote quote(String text, String author, String source, String category) {
        Quote q = new Quote(1, author, text, source);
        q.setCategory(category);
        return q;
    }

    private final Quote q = quote("Nothing", "Seneca", "Letters", "Stoic");

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }

    @Test
    public void matchesEachFieldIndependently() {
        assertTrue(QuoteQuery.forField(QuoteQuery.Field.QUOTE_TEXT, "noth").matches(q));
        assertTrue(QuoteQuery.forField(QuoteQuery.Field.AUTHOR, "sen").matches(q));
        assertTrue(QuoteQuery.forField(QuoteQuery.Field.SOURCE, "lett").matches(q));
        assertTrue(QuoteQuery.forField(QuoteQuery.Field.CATEGORY, "sto").matches(q));
    }

    @Test
    public void respectsFieldSet() {
        assertFalse(QuoteQuery.forField(QuoteQuery.Field.AUTHOR, "letters").matches(q));
        assertFalse(QuoteQuery.forField(QuoteQuery.Field.SOURCE, "seneca").matches(q));
        assertTrue(QuoteQuery.all("seneca").matches(q));
        assertFalse(QuoteQuery.all("seneca").toggled(QuoteQuery.Field.AUTHOR).matches(q));
    }

    @Test
    public void caseInsensitiveAndTrimmed() {
        assertTrue(QuoteQuery.all("  SENECA ").matches(q));
        assertEquals("seneca", QuoteQuery.all("  SENECA ").getText());
    }

    @Test
    public void belowMinimumLengthMatchesNothing() {
        assertFalse(QuoteQuery.all("se").matches(q));
        assertFalse(QuoteQuery.all(" se ").matches(q));
        assertTrue(QuoteQuery.all("sen").matches(q));
        assertTrue(QuoteQuery.all("se").filter(Arrays.asList(q)).isEmpty());
    }

    @Test
    public void filterKeepsMatchesInOrder() {
        Quote other = quote("x", "Plato", "", "");
        List<Quote> result = QuoteQuery.all("seneca").filter(Arrays.asList(other, q));
        assertEquals(1, result.size());
        assertSame(q, result.get(0));
    }

    @Test
    public void snippetMatchInMiddleHasBothEllipses() {
        String text = repeat("a", 100) + "NEEDLE" + repeat("b", 100);
        assertEquals("..." + repeat("a", 40) + "NEEDLE" + repeat("b", 60) + "...",
                QuoteQuery.all("needle").snippet(text));
    }

    @Test
    public void snippetMatchAtStartHasTrailingEllipsisOnly() {
        String text = "needle" + repeat("b", 100);
        assertEquals("needle" + repeat("b", 60) + "...", QuoteQuery.all("needle").snippet(text));
    }

    @Test
    public void snippetMatchAtEndHasLeadingEllipsisOnly() {
        String text = repeat("a", 100) + "needle";
        assertEquals("..." + repeat("a", 40) + "needle", QuoteQuery.all("needle").snippet(text));
    }

    @Test
    public void snippetShortTextWithMatchHasNoEllipsis() {
        assertEquals("a needle b", QuoteQuery.all("needle").snippet("a needle b"));
    }

    @Test
    public void snippetNoMatchTruncatesAt100() {
        assertEquals("short", QuoteQuery.all("needle").snippet("short"));
        assertEquals(repeat("a", 100), QuoteQuery.all("needle").snippet(repeat("a", 100)));
        assertEquals(repeat("a", 100) + "...", QuoteQuery.all("needle").snippet(repeat("a", 150)));
    }
}
