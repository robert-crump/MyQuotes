package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class QuoteQueryTest {
    private static Quote quote(String text, String author, String source, String tags) {
        Quote q = new Quote(1, author, text, source);
        q.setTags(tags.isEmpty() ? Arrays.<String>asList() : Arrays.asList(tags.split(" ")));
        return q;
    }

    private final Quote q = quote("Nothing", "Seneca", "Letters", "Stoic Life");

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
        assertTrue(QuoteQuery.forField(QuoteQuery.Field.HASHTAGS, "sto").matches(q));
    }

    @Test
    public void typedHashtagQueriesMatchByContainsOnEachTagIgnoringALeadingHash() {
        assertTrue(QuoteQuery.forField(QuoteQuery.Field.HASHTAGS, "toi").matches(q));
        assertTrue(QuoteQuery.forField(QuoteQuery.Field.HASHTAGS, "#sto").matches(q));
        assertTrue(QuoteQuery.forField(QuoteQuery.Field.HASHTAGS, "LIFE").matches(q));
        assertFalse(QuoteQuery.forField(QuoteQuery.Field.HASHTAGS, "stoiclife").matches(q));
        assertTrue(QuoteQuery.all("#life").matches(q));
        assertFalse(QuoteQuery.forField(QuoteQuery.Field.HASHTAGS, "sen").matches(q));
    }

    @Test
    public void exactTagQueriesMatchWholeTagsIgnoringCase() {
        QuoteQuery stoic = QuoteQuery.forTag("stoic");
        assertTrue(stoic.isExact());
        assertEquals("#stoic", stoic.getText());
        assertEquals(QuoteQuery.Field.HASHTAGS, stoic.singleField());
        assertTrue(stoic.matches(q));
        assertFalse(QuoteQuery.forTag("Sto").matches(q));
        assertFalse(QuoteQuery.forTag("Seneca").matches(q));
    }

    @Test
    public void exactTagQueriesSkipTheMinimumLength() {
        Quote ai = quote("x", "y", "", "AI Go");
        assertTrue(QuoteQuery.forTag("AI").isActive());
        assertTrue(QuoteQuery.forTag("ai").matches(ai));
        assertTrue(QuoteQuery.forTag("Go").matches(ai));
        assertFalse(QuoteQuery.forField(QuoteQuery.Field.HASHTAGS, "ai").matches(ai));
        assertEquals(Arrays.asList(ai), QuoteQuery.forTag("AI").filter(Arrays.asList(ai, q)));
    }

    @Test
    public void retypingAnExactQueryMakesItATypedOne() {
        QuoteQuery exact = QuoteQuery.forTag("Stoic");
        assertTrue(exact.withText(" #Stoic ").isExact());
        assertFalse(exact.withText("#Stoi").isExact());
        assertFalse(exact.scopedTo(QuoteQuery.Field.HASHTAGS).isExact());
        assertNotEquals(QuoteQuery.forField(QuoteQuery.Field.HASHTAGS, "#Stoic"), exact);
    }

    @Test
    public void snippetShowsTheTagLineWhenTheMatchIsOnATag() {
        assertEquals("#Life #Stoic", QuoteQuery.forTag("Stoic").snippet(q));
        assertEquals("#Life #Stoic", QuoteQuery.all("stoic").snippet(q));
        assertEquals("Nothing", QuoteQuery.all("noth").snippet(q));
        assertEquals("Nothing", QuoteQuery.all("seneca").snippet(q));
    }

    @Test
    public void respectsFieldSet() {
        assertFalse(QuoteQuery.forField(QuoteQuery.Field.AUTHOR, "letters").matches(q));
        assertFalse(QuoteQuery.forField(QuoteQuery.Field.SOURCE, "seneca").matches(q));
        assertTrue(QuoteQuery.all("seneca").matches(q));
    }

    @Test
    public void scopedToSwitchesBetweenOneFieldAndAllKeepingText() {
        QuoteQuery author = QuoteQuery.all("seneca").scopedTo(QuoteQuery.Field.AUTHOR);
        assertEquals(QuoteQuery.forField(QuoteQuery.Field.AUTHOR, "seneca"), author);
        assertEquals(QuoteQuery.Field.AUTHOR, author.singleField());
        assertFalse(author.scopedTo(QuoteQuery.Field.SOURCE).matches(q));
        QuoteQuery all = author.scopedTo(null);
        assertEquals(QuoteQuery.all("seneca"), all);
        assertEquals(null, all.singleField());
    }

    @Test
    public void caseInsensitiveMatchingButOriginalCasePreserved() {
        assertTrue(QuoteQuery.all("  SENECA ").matches(q));
        assertEquals("SENECA", QuoteQuery.all("  SENECA ").getText());
    }

    @Test
    public void belowMinimumLengthMatchesNothing() {
        assertFalse(QuoteQuery.all("se").matches(q));
        assertFalse(QuoteQuery.all(" se ").matches(q));
        assertTrue(QuoteQuery.all("sen").matches(q));
    }

    @Test
    public void filterBelowMinimumLengthReturnsEveryQuoteNewestFirst() {
        Quote older = quote("x", "Plato", "", "");
        older.setId(1);
        Quote newer = quote("y", "Kant", "", "");
        newer.setId(2);
        assertEquals(Arrays.asList(newer, older), QuoteQuery.all("").filter(Arrays.asList(older, newer)));
        assertEquals(Arrays.asList(newer, older), QuoteQuery.all("se").filter(Arrays.asList(older, newer)));
    }

    @Test
    public void filterReturnsOnlyMatches() {
        Quote other = quote("x", "Plato", "", "");
        List<Quote> result = QuoteQuery.all("seneca").filter(Arrays.asList(other, q));
        assertEquals(1, result.size());
        assertSame(q, result.get(0));
    }

    @Test
    public void filterSortsMatchesNewestFirst() {
        Quote early = quote("a", "Seneca", "", "");
        early.setId(5);
        early.setAddedAt(1_000L);
        Quote late = quote("b", "Seneca", "", "");
        late.setId(3);
        late.setAddedAt(2_000L);
        Quote unknown = quote("c", "Seneca", "", "");
        unknown.setId(9);
        assertEquals(Arrays.asList(late, early, unknown),
                QuoteQuery.all("seneca").filter(Arrays.asList(unknown, early, late)));
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
