package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QuoteTest {
    @Test
    public void fourArgConstructorSetsFields() {
        Quote q = new Quote(7, "Seneca", "Text", "Letters");
        assertEquals(Integer.valueOf(7), q.getId());
        assertEquals("Seneca", q.getAuthor());
        assertEquals("Text", q.getQuoteText());
        assertEquals("Letters", q.getSource());
        assertEquals("", q.getCategory());
        assertFalse(q.isFavorite());
        assertEquals(0, q.getTimesShown());
    }

    @Test
    public void nullFieldsReadBackAsEmptyStrings() {
        Quote q = new Quote(1, null, null, null);
        q.setCategory(null);
        assertEquals("", q.getAuthor());
        assertEquals("", q.getQuoteText());
        assertEquals("", q.getSource());
        assertEquals("", q.getCategory());
    }

    @Test
    public void toggleFavoriteSetsAndClearsTimestamp() {
        Quote q = new Quote(1, "a", "t", "s");
        long before = System.currentTimeMillis();
        q.toggleFavorite();
        assertTrue(q.isFavorite());
        assertTrue(q.getFavoritedAt() >= before);
        q.toggleFavorite();
        assertFalse(q.isFavorite());
        assertEquals(0L, q.getFavoritedAt());
    }

    @Test
    public void recordViewIncrementsCountAndSetsLastShown() {
        Quote q = new Quote(1, "a", "t", "s");
        long before = System.currentTimeMillis();
        q.recordView();
        q.recordView();
        assertEquals(2, q.getTimesShown());
        assertTrue(q.getLastShown() >= before);
    }

    @Test
    public void equalityAndHashAreByIdOnly() {
        Quote a = new Quote(1, "a", "t", "s");
        Quote b = new Quote(1, "other", "other", "other");
        Quote c = new Quote(2, "a", "t", "s");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, null);
    }
}
