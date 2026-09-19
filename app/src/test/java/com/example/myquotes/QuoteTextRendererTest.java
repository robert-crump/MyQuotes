package com.example.myquotes;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QuoteTextRendererTest {

    @Test
    public void textUnderLimit_isReturnedUnchanged() {
        String text = "Short quote.";
        assertEquals(text, QuoteTextRenderer.truncate(text, 150));
    }

    @Test
    public void textExactlyAtLimit_isReturnedUnchanged() {
        String text = "1234567890";
        assertEquals(text, QuoteTextRenderer.truncate(text, 10));
    }

    @Test
    public void cutsAtLastSentenceEndingWithinLimit() {
        // "First sentence. Second sentence." -- limit lands inside the third word,
        // so only the first complete sentence fits.
        String text = "First sentence. Second sentence.";
        assertEquals("First sentence. (...)", QuoteTextRenderer.truncate(text, 20));
    }

    @Test
    public void packsInAsManySentencesAsFitUnderLimit() {
        String text = "One. Two. Three. Four. Five.";
        // Limit covers "One. Two. Three." (16 chars) plus part of " Four."
        assertEquals("One. Two. Three. (...)", QuoteTextRenderer.truncate(text, 20));
    }

    @Test
    public void fallsBackToWordBoundaryWhenNoPunctuationWithinLimit() {
        String text = "This quote has no punctuation before the limit is reached here";
        // First 20 chars: "This quote has no p" -- no sentence-ending punctuation.
        assertEquals("This quote has no (...)", QuoteTextRenderer.truncate(text, 20));
    }

    @Test
    public void hardCutsWhenNoPunctuationOrSpaceWithinLimit() {
        String text = "Supercalifragilisticexpialidocious and more text after it";
        assertEquals("Supercalifragil (...)", QuoteTextRenderer.truncate(text, 15));
    }

    private static Quote quote(String author, String text, String source) {
        return new Quote(1, author, text, source);
    }

    @Test
    public void shareText_withSource() {
        assertEquals("\"Text\"\n\n— Author (Book)",
                QuoteTextRenderer.shareText(quote("Author", "Text", "Book")));
    }

    @Test
    public void shareText_withoutSource() {
        assertEquals("\"Text\"\n\n— Author",
                QuoteTextRenderer.shareText(quote("Author", "Text", "")));
    }

    @Test
    public void shareText_authorOnlyQuoteWithNullSource() {
        assertEquals("\"Text\"\n\n— Author",
                QuoteTextRenderer.shareText(quote("Author", "Text", null)));
    }

    @Test
    public void notificationTitle_usesAuthorWhenPresent() {
        assertEquals("Quote by Ann", QuoteTextRenderer.notificationTitle(
                quote("Ann", "T", ""), "Quote", a -> "Quote by " + a));
    }

    @Test
    public void notificationTitle_fallsBackWhenAuthorEmpty() {
        assertEquals("Quote", QuoteTextRenderer.notificationTitle(
                quote("", "T", ""), "Quote", a -> "Quote by " + a));
        assertEquals("Quote", QuoteTextRenderer.notificationTitle(
                quote(null, "T", ""), "Quote", a -> "Quote by " + a));
    }

    @Test
    public void notificationBodies_useTheirLimits() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) sb.append("word ");
        Quote q = quote("A", sb.toString().trim(), "");
        assertEquals(true, QuoteTextRenderer.notificationContent(q).endsWith(" (...)"));
        assertEquals(true, QuoteTextRenderer.notificationContent(q).length() <= 150 + 6);
        assertEquals(true, QuoteTextRenderer.notificationBigText(q).length() <= 300 + 6);
        assertEquals(true, QuoteTextRenderer.notificationBigText(q).length() > 156);
    }
}
