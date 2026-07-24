package com.example.myquotes.notifications;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QuoteTextUtilsTest {

    @Test
    public void textUnderLimit_isReturnedUnchanged() {
        String text = "Short quote.";
        assertEquals(text, QuoteTextUtils.truncate(text, 150));
    }

    @Test
    public void textExactlyAtLimit_isReturnedUnchanged() {
        String text = "1234567890";
        assertEquals(text, QuoteTextUtils.truncate(text, 10));
    }

    @Test
    public void cutsAtLastSentenceEndingWithinLimit() {
        // "First sentence. Second sentence." -- limit lands inside the third word,
        // so only the first complete sentence fits.
        String text = "First sentence. Second sentence.";
        assertEquals("First sentence. (...)", QuoteTextUtils.truncate(text, 20));
    }

    @Test
    public void packsInAsManySentencesAsFitUnderLimit() {
        String text = "One. Two. Three. Four. Five.";
        // Limit covers "One. Two. Three." (16 chars) plus part of " Four."
        assertEquals("One. Two. Three. (...)", QuoteTextUtils.truncate(text, 20));
    }

    @Test
    public void fallsBackToWordBoundaryWhenNoPunctuationWithinLimit() {
        String text = "This quote has no punctuation before the limit is reached here";
        // First 20 chars: "This quote has no p" -- no sentence-ending punctuation.
        assertEquals("This quote has no (...)", QuoteTextUtils.truncate(text, 20));
    }

    @Test
    public void hardCutsWhenNoPunctuationOrSpaceWithinLimit() {
        String text = "Supercalifragilisticexpialidocious and more text after it";
        assertEquals("Supercalifragil (...)", QuoteTextUtils.truncate(text, 15));
    }
}
