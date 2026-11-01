package com.example.myquotes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The add/edit form, as it would be saved: the three text fields trimmed, the hashtags
 * normalized ({@link Hashtag#normalizeAll}, so order and duplicates don't count), and the
 * rejected suggestions (#55) likewise, minus any tag the form has (saving drops those).
 * AddEditActivity keeps a baseline — empty in Add mode, the loaded quote in Edit mode — and asks
 * for a discard confirmation only when the current form differs from it.
 */
final class QuoteFormSnapshot {
    static final QuoteFormSnapshot EMPTY =
            new QuoteFormSnapshot("", "", "", Collections.emptyList());

    private final String author;
    private final String quoteText;
    private final String source;
    private final List<String> tags;
    private final List<String> rejectedTags;

    QuoteFormSnapshot(String author, String quoteText, String source, List<String> tags) {
        this(author, quoteText, source, tags, null);
    }

    QuoteFormSnapshot(String author, String quoteText, String source, List<String> tags,
                      List<String> rejectedTags) {
        this.author = trim(author);
        this.quoteText = trim(quoteText);
        this.source = trim(source);
        this.tags = Hashtag.normalizeAll(tags);
        this.rejectedTags = new ArrayList<>();
        for (String tag : Hashtag.normalizeAll(rejectedTags)) {
            if (!Hashtag.containsIgnoreCase(this.tags, tag)) this.rejectedTags.add(tag);
        }
    }

    static QuoteFormSnapshot of(Quote quote) {
        return new QuoteFormSnapshot(quote.getAuthor(), quote.getQuoteText(),
                quote.getSource(), quote.getTags(), quote.getRejectedTags());
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
                && tags.equals(other.tags)
                && rejectedTags.equals(other.rejectedTags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(author, quoteText, source, tags, rejectedTags);
    }
}
