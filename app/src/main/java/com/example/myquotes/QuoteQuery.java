package com.example.myquotes;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable search predicate: trimmed query text (original case preserved for display,
 * matched case-insensitively), the fields it is scoped to, and an optional hashtag filter.
 * Owns matching, the minimum-length rule and result snippets, and is the single protocol for
 * opening SearchActivity (or handing SearchFragment) a query. Text matches by contains (on each
 * hashtag, ignoring a leading {@code #}). The hashtag filter (#51, ADR-005) is one whole tag,
 * compared ignoring case, "no hashtag", or "has suggestions" (#55); it combines with the text and
 * needs no minimum length. "Has suggestions" depends on the suggestion model, which this class
 * doesn't know: it lets every quote through, and {@link SuggestionRanking} keeps the ones with
 * suggestions.
 */
public final class QuoteQuery {
    public enum Field { QUOTE_TEXT, AUTHOR, SOURCE, HASHTAGS }

    public static final int MIN_LENGTH = 3;
    private static final int SNIPPET_BEFORE = 40;
    private static final int SNIPPET_AFTER = 60;
    private static final int SNIPPET_FALLBACK_LENGTH = 100;

    static final String EXTRA_TEXT = "search_query";
    static final String EXTRA_FIELDS = "search_fields";
    static final String EXTRA_TAG = "search_tag";
    static final String EXTRA_SUGGESTED = "search_suggested";

    private final String text;
    private final EnumSet<Field> fields;
    // The hashtag filter: null for none, "" for "no hashtag" (a tag is never empty), else a tag.
    @Nullable
    private final String tag;
    // The "has suggestions" filter; tag is then null.
    private final boolean suggested;

    private QuoteQuery(String text, Set<Field> fields, @Nullable String tag, boolean suggested) {
        this.text = text == null ? "" : text.trim();
        this.fields = EnumSet.noneOf(Field.class);
        this.fields.addAll(fields);
        this.tag = suggested ? null : tag;
        this.suggested = suggested;
    }

    private QuoteQuery(String text, Set<Field> fields, @Nullable String tag) {
        this(text, fields, tag, false);
    }

    private QuoteQuery(String text, Set<Field> fields) {
        this(text, fields, null);
    }

    /** Query over all four fields, as produced by typing in the search box. */
    public static QuoteQuery all(String text) {
        return new QuoteQuery(text, EnumSet.allOf(Field.class));
    }

    /** Query scoped to a single field, as produced by field-click navigation. */
    public static QuoteQuery forField(Field field, String text) {
        return new QuoteQuery(text, EnumSet.of(field));
    }

    /** The quotes with one hashtag (a tapped tag): no text, filtered to {@code tag}. */
    public static QuoteQuery forTag(String tag) {
        return all("").withTag(tag);
    }

    /** The quotes without any hashtag: no text, "no hashtag" filter. */
    public static QuoteQuery untagged() {
        return all("").withUntagged();
    }

    /** The quotes with hashtag suggestions (#55): no text, "has suggestions" filter. */
    public static QuoteQuery suggested() {
        return all("").withSuggested();
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

    /** The filter tag, or null when the filter is off or "no hashtag". */
    @Nullable
    public String getTag() {
        return tag == null || tag.isEmpty() ? null : tag;
    }

    /** Whether the filter is "no hashtag". */
    public boolean isUntagged() {
        return tag != null && tag.isEmpty();
    }

    /** Whether the filter is "has suggestions" (#55). */
    public boolean isSuggested() {
        return suggested;
    }

    /** Whether the results are ranked by their suggestions: "no hashtag" or "has suggestions". */
    public boolean isRankedBySuggestions() {
        return suggested || isUntagged();
    }

    public boolean hasTagFilter() {
        return tag != null || suggested;
    }

    /** Same text and fields, filtered to {@code newTag}. */
    public QuoteQuery withTag(String newTag) {
        String normalized = Hashtag.normalize(newTag);
        return new QuoteQuery(text, fields, normalized.isEmpty() ? null : normalized);
    }

    /** Same text and fields, filtered to quotes without a hashtag. */
    public QuoteQuery withUntagged() {
        return new QuoteQuery(text, fields, "");
    }

    /** Same text and fields, filtered to quotes with hashtag suggestions. */
    public QuoteQuery withSuggested() {
        return new QuoteQuery(text, fields, null, true);
    }

    public QuoteQuery withoutTagFilter() {
        return new QuoteQuery(text, fields, null);
    }

    /** New text; fields and hashtag filter stay. */
    public QuoteQuery withText(String newText) {
        return new QuoteQuery(newText, fields, tag, suggested);
    }

    /**
     * Same text and hashtag filter, scoped to {@code field}, or to all four fields when null
     * (the search filter).
     */
    public QuoteQuery scopedTo(@Nullable Field field) {
        return new QuoteQuery(text, field == null ? EnumSet.allOf(Field.class) : EnumSet.of(field), tag,
                suggested);
    }

    /** The one field this query is scoped to, or null when it covers several (e.g. all). */
    @Nullable
    public Field singleField() {
        return fields.size() == 1 ? fields.iterator().next() : null;
    }

    /** Whether anything narrows the results: text of at least {@link #MIN_LENGTH} or the filter. */
    public boolean isActive() {
        return isTextActive() || hasTagFilter();
    }

    private boolean isTextActive() {
        return text.length() >= MIN_LENGTH;
    }

    public boolean matches(Quote quote) {
        if (!isActive()) return false;
        if (tag != null && !passesTagFilter(quote.getTags())) return false;
        if (!isTextActive()) return true;
        return (fields.contains(Field.QUOTE_TEXT) && matchesValue(quote.getQuoteText()))
                || (fields.contains(Field.AUTHOR) && matchesValue(quote.getAuthor()))
                || (fields.contains(Field.SOURCE) && matchesValue(quote.getSource()))
                || (fields.contains(Field.HASHTAGS) && matchesTags(quote.getTags()));
    }

    /**
     * Matching quotes, newest first ({@link Quote#NEWEST_FIRST}). An inactive query (text below
     * {@link #MIN_LENGTH}, no hashtag filter) returns every quote, so the search screen doubles
     * as the full list.
     */
    public List<Quote> filter(List<Quote> quotes) {
        List<Quote> results = new ArrayList<>();
        if (quotes == null) return results;
        for (Quote quote : quotes) {
            if (!isActive() || matches(quote)) results.add(quote);
        }
        results.sort(Quote.NEWEST_FIRST);
        return results;
    }

    /**
     * The result line for {@code quote}: its tag line when the typed text matches a tag and not
     * the quote text, otherwise an excerpt of the quote text ({@link #snippet(String)}); with
     * only the hashtag filter, the start of the quote.
     */
    public String snippet(Quote quote) {
        String quoteText = quote.getQuoteText();
        boolean inText = fields.contains(Field.QUOTE_TEXT) && isTextActive() && matchesValue(quoteText);
        if (!inText && fields.contains(Field.HASHTAGS) && isTextActive() && matchesTags(quote.getTags())) {
            return Hashtag.line(quote.getTags());
        }
        return snippet(quoteText);
    }

    /** Excerpt of {@code quoteText} around the first match, with ellipses where cut. */
    public String snippet(String quoteText) {
        int index = isTextActive() ? quoteText.toLowerCase().indexOf(text.toLowerCase()) : -1;
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

    private boolean matchesValue(@Nullable String value) {
        return value != null && value.toLowerCase().contains(text.toLowerCase());
    }

    private boolean passesTagFilter(List<String> tags) {
        if (tag.isEmpty()) return tags.isEmpty();
        for (String t : tags) {
            if (t.equalsIgnoreCase(tag)) return true;
        }
        return false;
    }

    private boolean matchesTags(List<String> tags) {
        String needle = Hashtag.stripHash(text);
        if (needle.isEmpty()) return false;
        String key = Hashtag.key(needle);
        for (String t : tags) {
            if (Hashtag.key(t).contains(key)) return true;
        }
        return false;
    }

    /** The one way to open SearchActivity for a query. */
    @NonNull
    public Intent toIntent(Context context) {
        Intent intent = new Intent(context, SearchActivity.class);
        intent.putExtras(toBundle());
        return intent;
    }

    /** Reads back a query written by {@link #toIntent}; null if the intent carries none. */
    @Nullable
    public static QuoteQuery fromIntent(Intent intent) {
        String text = intent.getStringExtra(EXTRA_TEXT);
        if ((text == null || text.isEmpty()) && intent.getStringExtra(EXTRA_TAG) == null
                && !intent.getBooleanExtra(EXTRA_SUGGESTED, false)) {
            return null;
        }
        return fromBundle(intent.getExtras());
    }

    /** The query as fragment arguments / saved state; read back with {@link #fromBundle}. */
    @NonNull
    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_TEXT, text);
        ArrayList<String> names = new ArrayList<>();
        for (Field f : fields) names.add(f.name());
        bundle.putStringArrayList(EXTRA_FIELDS, names);
        if (tag != null) bundle.putString(EXTRA_TAG, tag);
        if (suggested) bundle.putBoolean(EXTRA_SUGGESTED, true);
        return bundle;
    }

    /** Reads back a query written by {@link #toBundle}; null if the bundle carries none. */
    @Nullable
    public static QuoteQuery fromBundle(@Nullable Bundle bundle) {
        if (bundle == null) return null;
        String text = bundle.getString(EXTRA_TEXT);
        if (text == null) return null;
        ArrayList<String> names = bundle.getStringArrayList(EXTRA_FIELDS);
        String tag = bundle.getString(EXTRA_TAG);
        boolean suggested = bundle.getBoolean(EXTRA_SUGGESTED, false);
        if (names == null) return new QuoteQuery(text, EnumSet.allOf(Field.class), tag, suggested);
        Set<Field> fields = EnumSet.noneOf(Field.class);
        for (String name : names) {
            try {
                fields.add(Field.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // unknown field name: skip
            }
        }
        return new QuoteQuery(text, fields, tag, suggested);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuoteQuery)) return false;
        QuoteQuery other = (QuoteQuery) o;
        return text.equals(other.text) && fields.equals(other.fields) && Objects.equals(tag, other.tag)
                && suggested == other.suggested;
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, fields, tag, suggested);
    }
}
