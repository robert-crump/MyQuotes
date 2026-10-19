package com.example.myquotes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The order of the "No hashtag" list and of the tagging run (#53): untagged quotes ranked by how
 * good their hashtag suggestions are, so the ones that only need a suggestion accepted come first.
 * Pure. A quote's suggestions come from {@link HashtagSuggester#suggestionsFor} on a model fitted
 * on the whole collection, so they are the ones the editor would show. Ranked by the strength of
 * the top suggestion (higher share, then higher count), ties by the 2nd, then the 3rd; quotes
 * without suggestions last; then newest first ({@link Quote#NEWEST_FIRST}).
 */
final class UntaggedRanking {

    /** A quote with its suggestions, best first (empty when there are none, or it has hashtags). */
    static final class Entry {
        final Quote quote;
        final List<HashtagSuggester.Suggestion> suggestions;

        Entry(Quote quote, List<HashtagSuggester.Suggestion> suggestions) {
            this.quote = quote;
            this.suggestions = suggestions;
        }
    }

    static final Comparator<Entry> ORDER = (a, b) -> {
        int n = Math.max(a.suggestions.size(), b.suggestions.size());
        for (int i = 0; i < n; i++) {
            if (i >= a.suggestions.size()) return 1;
            if (i >= b.suggestions.size()) return -1;
            int byStrength = HashtagSuggester.byStrength(a.suggestions.get(i).strongest, b.suggestions.get(i).strongest);
            if (byStrength != 0) return byStrength;
        }
        return Quote.NEWEST_FIRST.compare(a.quote, b.quote);
    };

    private UntaggedRanking() {
    }

    /** {@code quotes} (normally the "No hashtag" results) with their suggestions, best first. */
    static List<Entry> rank(List<Quote> quotes, HashtagSuggester model) {
        List<Entry> entries = new ArrayList<>(quotes.size());
        for (Quote quote : quotes) {
            entries.add(new Entry(quote, quote.getTags().isEmpty()
                    ? model.suggestionsFor(quote) : Collections.emptyList()));
        }
        entries.sort(ORDER);
        return entries;
    }

    /**
     * The tagging run's next quote: the best-ranked quote matching {@code query} that is not in
     * {@code skipped}; null when there is none. {@code model} is fitted on {@code quotes}.
     */
    static Quote next(List<Quote> quotes, QuoteQuery query, HashtagSuggester model, Set<Integer> skipped) {
        for (Entry entry : rank(candidates(quotes, query, skipped), model)) return entry.quote;
        return null;
    }

    /** How many quotes the run still has: those matching {@code query} that are not skipped. */
    static int remaining(List<Quote> quotes, QuoteQuery query, Set<Integer> skipped) {
        return candidates(quotes, query, skipped).size();
    }

    private static List<Quote> candidates(List<Quote> quotes, QuoteQuery query, Set<Integer> skipped) {
        List<Quote> candidates = query.filter(quotes);
        candidates.removeIf(quote -> skipped.contains(quote.getId()));
        return candidates;
    }
}
