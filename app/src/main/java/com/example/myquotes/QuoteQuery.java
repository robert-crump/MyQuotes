package com.example.myquotes;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable search predicate: trimmed query text (original case preserved for display,
 * matched case-insensitively) plus the fields it is scoped to. Owns matching, the
 * minimum-length rule and result snippets, and is the single protocol for opening
 * SearchActivity for a query.
 */
public final class QuoteQuery {
    public enum Field { QUOTE_TEXT, AUTHOR, SOURCE, CATEGORY }

    public static final int MIN_LENGTH = 3;
    private static final int SNIPPET_BEFORE = 40;
    private static final int SNIPPET_AFTER = 60;
    private static final int SNIPPET_FALLBACK_LENGTH = 100;

    static final String EXTRA_TEXT = "search_query";
    static final String EXTRA_FIELDS = "search_fields";

    private final String text;
    private final EnumSet<Field> fields;

    private QuoteQuery(String text, Set<Field> fields) {
        this.text = text == null ? "" : text.trim();
        this.fields = EnumSet.noneOf(Field.class);
        this.fields.addAll(fields);
    }

    /** Query over all four fields, as produced by typing in the search box. */
    public static QuoteQuery all(String text) {
        return new QuoteQuery(text, EnumSet.allOf(Field.class));
    }

    /** Query scoped to a single field, as produced by field-click navigation. */
    public static QuoteQuery forField(Field field, String text) {
        return new QuoteQuery(text, EnumSet.of(field));
    }

    public String getText() {
        return text;
    }

    public Set<Field> getFields() {
        return Collections.unmodifiableSet(fields);
    }

    public boolean hasField(Field field) {
        return fields.contains(field);
    }

    public QuoteQuery withText(String newText) {
        return new QuoteQuery(newText, fields);
    }

    public QuoteQuery toggled(Field field) {
        EnumSet<Field> next = EnumSet.copyOf(fields.isEmpty() ? EnumSet.noneOf(Field.class) : fields);
        if (!next.remove(field)) {
            next.add(field);
        }
        return new QuoteQuery(text, next);
    }

    public boolean isActive() {
        return text.length() >= MIN_LENGTH;
    }

    public boolean matches(Quote quote) {
        if (!isActive()) return false;
        return (fields.contains(Field.QUOTE_TEXT) && contains(quote.getQuoteText()))
                || (fields.contains(Field.AUTHOR) && contains(quote.getAuthor()))
                || (fields.contains(Field.SOURCE) && contains(quote.getSource()))
                || (fields.contains(Field.CATEGORY) && contains(quote.getCategory()));
    }

    public List<Quote> filter(List<Quote> quotes) {
        List<Quote> results = new ArrayList<>();
        if (!isActive() || quotes == null) return results;
        for (Quote quote : quotes) {
            if (matches(quote)) results.add(quote);
        }
        return results;
    }

    /** Excerpt of {@code quoteText} around the first match, with ellipses where cut. */
    public String snippet(String quoteText) {
        int index = text.isEmpty() ? -1 : quoteText.toLowerCase().indexOf(text.toLowerCase());
        if (index == -1) {
            return quoteText.length() <= SNIPPET_FALLBACK_LENGTH
                    ? quoteText
                    : quoteText.substring(0, SNIPPET_FALLBACK_LENGTH) + "...";
        }
        int start = Math.max(0, index - SNIPPET_BEFORE);
        int end = Math.min(quoteText.length(), index + text.length() + SNIPPET_AFTER);

        String snippet = quoteText.substring(start, end);
        if (start > 0) snippet = "..." + snippet;
        if (end < quoteText.length()) snippet = snippet + "...";
        return snippet;
    }

    private boolean contains(@Nullable String value) {
        return value != null && value.toLowerCase().contains(text.toLowerCase());
    }

    /** The one way to open SearchActivity for a query. */
    @NonNull
    public Intent toIntent(Context context) {
        Intent intent = new Intent(context, SearchActivity.class);
        intent.putExtra(EXTRA_TEXT, text);
        ArrayList<String> names = new ArrayList<>();
        for (Field f : fields) names.add(f.name());
        intent.putStringArrayListExtra(EXTRA_FIELDS, names);
        return intent;
    }

    /** Reads back a query written by {@link #toIntent}; null if the intent carries none. */
    @Nullable
    public static QuoteQuery fromIntent(Intent intent) {
        String text = intent.getStringExtra(EXTRA_TEXT);
        if (text == null || text.isEmpty()) return null;
        ArrayList<String> names = intent.getStringArrayListExtra(EXTRA_FIELDS);
        if (names == null) return all(text);
        Set<Field> fields = EnumSet.noneOf(Field.class);
        for (String name : names) {
            try {
                fields.add(Field.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // unknown field name: skip
            }
        }
        return new QuoteQuery(text, fields);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuoteQuery)) return false;
        QuoteQuery other = (QuoteQuery) o;
        return text.equals(other.text) && fields.equals(other.fields);
    }

    @Override
    public int hashCode() {
        return 31 * text.hashCode() + fields.hashCode();
    }
}
