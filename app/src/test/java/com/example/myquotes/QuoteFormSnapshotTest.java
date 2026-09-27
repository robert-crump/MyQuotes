package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class QuoteFormSnapshotTest {

    @Test
    public void blankFieldsEqualEmpty() {
        assertEquals(QuoteFormSnapshot.EMPTY, new QuoteFormSnapshot("", " ", null, "\n"));
    }

    @Test
    public void anyNonBlankFieldDiffersFromEmpty() {
        assertNotEquals(QuoteFormSnapshot.EMPTY, new QuoteFormSnapshot("a", "", "", ""));
        assertNotEquals(QuoteFormSnapshot.EMPTY, new QuoteFormSnapshot("", "a", "", ""));
        assertNotEquals(QuoteFormSnapshot.EMPTY, new QuoteFormSnapshot("", "", "a", ""));
        assertNotEquals(QuoteFormSnapshot.EMPTY, new QuoteFormSnapshot("", "", "", "a"));
    }

    @Test
    public void matchesLoadedQuoteIgnoringSurroundingWhitespace() {
        Quote quote = new Quote();
        quote.setAuthor("Seneca");
        quote.setQuoteText("Luck is what happens when preparation meets opportunity.");
        quote.setSource("");
        quote.setCategory("Stoicism");

        assertEquals(QuoteFormSnapshot.of(quote), new QuoteFormSnapshot(" Seneca ",
                "Luck is what happens when preparation meets opportunity.\n", "", "Stoicism"));
        assertNotEquals(QuoteFormSnapshot.of(quote), new QuoteFormSnapshot("Seneca",
                "Luck is what happens when preparation meets opportunity!", "", "Stoicism"));
    }
}
