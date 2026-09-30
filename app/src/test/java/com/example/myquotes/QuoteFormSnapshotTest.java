package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class QuoteFormSnapshotTest {
    private static final List<String> NONE = Collections.emptyList();

    @Test
    public void blankFieldsEqualEmpty() {
        assertEquals(QuoteFormSnapshot.EMPTY, new QuoteFormSnapshot("", " ", null, null));
    }

    @Test
    public void anyNonBlankFieldDiffersFromEmpty() {
        assertNotEquals(QuoteFormSnapshot.EMPTY, new QuoteFormSnapshot("a", "", "", NONE));
        assertNotEquals(QuoteFormSnapshot.EMPTY, new QuoteFormSnapshot("", "a", "", NONE));
        assertNotEquals(QuoteFormSnapshot.EMPTY, new QuoteFormSnapshot("", "", "a", NONE));
        assertNotEquals(QuoteFormSnapshot.EMPTY, new QuoteFormSnapshot("", "", "", Arrays.asList("a")));
    }

    @Test
    public void matchesLoadedQuoteIgnoringSurroundingWhitespace() {
        Quote quote = new Quote();
        quote.setAuthor("Seneca");
        quote.setQuoteText("Luck is what happens when preparation meets opportunity.");
        quote.setSource("");
        quote.setTags(Arrays.asList("Stoicism", "Luck"));

        assertEquals(QuoteFormSnapshot.of(quote), new QuoteFormSnapshot(" Seneca ",
                "Luck is what happens when preparation meets opportunity.\n", "",
                Arrays.asList("Stoicism", "Luck")));
        assertNotEquals(QuoteFormSnapshot.of(quote), new QuoteFormSnapshot("Seneca",
                "Luck is what happens when preparation meets opportunity!", "",
                Arrays.asList("Luck", "Stoicism")));
    }

    @Test
    public void comparesTheTagListIgnoringOrder() {
        QuoteFormSnapshot base = new QuoteFormSnapshot("a", "t", "", Arrays.asList("B", "A"));
        assertEquals(base, new QuoteFormSnapshot("a", "t", "", Arrays.asList("#A", "B")));
        assertNotEquals(base, new QuoteFormSnapshot("a", "t", "", Arrays.asList("A")));
        assertNotEquals(base, new QuoteFormSnapshot("a", "t", "", Arrays.asList("A", "B", "C")));
    }
}
