package com.example.myquotes;

import java.util.Objects;

/**
 * The four add/edit form fields, trimmed (as they would be saved). AddEditActivity keeps a
 * baseline — empty in Add mode, the loaded quote in Edit mode — and asks for a discard
 * confirmation only when the current form differs from it.
 */
final class QuoteFormSnapshot {
    static final QuoteFormSnapshot EMPTY = new QuoteFormSnapshot("", "", "", "");

    private final String author;
    private final String quoteText;
    private final String source;
    private final String category;

    QuoteFormSnapshot(String author, String quoteText, String source, String category) {
        this.author = trim(author);
        this.quoteText = trim(quoteText);
        this.source = trim(source);
        this.category = trim(category);
    }

    static QuoteFormSnapshot of(Quote quote) {
        return new QuoteFormSnapshot(quote.getAuthor(), quote.getQuoteText(),
                quote.getSource(), quote.getCategory());
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuoteFormSnapshot)) return false;
        QuoteFormSnapshot other = (QuoteFormSnapshot) o;
        return author.equals(other.author)
                && quoteText.equals(other.quoteText)
                && source.equals(other.source)
                && category.equals(other.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(author, quoteText, source, category);
    }
}
