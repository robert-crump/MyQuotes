package com.example.myquotes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The order of the ranked "No hashtag" and "Has suggestions" lists and of their tagging runs (#53,
 * #55): quotes ranked by how good their hashtag suggestions are, so the ones that only need a
 * suggestion accepted come first. Pure. A quote's suggestions come from
 * {@link HashtagSuggester#suggest(Quote)} on a model fitted on the whole collection, so they are
 * the ones the editor would show. Ranked by the strength of the top suggestion (higher share, then
 * higher count), ties by the 2nd, then the 3rd; quotes without suggestions last (the "Has
 * suggestions" list drops them); then newest first ({@link Quote#NEWEST_FIRST}).
 * <p>
 * The "Suggested for" list of one tag (#56) gives each quote only that tag, from
 * {@link HashtagSuggester#suggestionFor} (whether or not it is among the quote's top 3), so it is
 * ranked by that tag's strongest reason, and drops the quotes it isn't suggested for.
 */
final class SuggestionRanking {

    /** A quote with its suggestions, best first (empty when there are none). */
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

    private SuggestionRanking() {
    }

    /** {@code quotes} with their suggestions, best first; {@code model} is fitted on the collection. */
    static List<Entry> rank(List<Quote> quotes, HashtagSuggester model) {
        return rank(quotes, model::suggest);
    }

    /** {@code quotes} with their suggestions ({@code suggestions}: per quote), best first. */
    static List<Entry> rank(List<Quote> quotes, Function<Quote, List<HashtagSuggester.Suggestion>> suggestions) {
        List<Entry> entries = new ArrayList<>(quotes.size());
        for (Quote quote : quotes) entries.add(new Entry(quote, suggestions.apply(quote)));
        entries.sort(ORDER);
        return entries;
    }

    /**
     * {@code query}'s results ({@link QuoteQuery#filter}) ranked, without those in {@code skipped};
     * for "has suggestions" and "suggested for" only the quotes with suggestions. {@code model} is fitted on
     * {@code quotes}.
     */
    static List<Entry> results(List<Quote> quotes, QuoteQuery query, HashtagSuggester model, Set<Integer> skipped) {
        return results(quotes, query, suggestionsOf(query, model), skipped);
    }

    /**
     * Each quote's suggestions in {@code query}'s list: for "suggested for" only that tag (or
     * none), else {@link HashtagSuggester#suggest(Quote)}. {@code model} is fitted on the collection.
     */
    static Function<Quote, List<HashtagSuggester.Suggestion>> suggestionsOf(QuoteQuery query, HashtagSuggester model) {
        String tag = query.getSuggestedTag();
        if (tag == null) return model::suggest;
        return quote -> {
            HashtagSuggester.Suggestion suggestion = model.suggestionFor(quote, tag);
            return suggestion == null ? Collections.emptyList() : Collections.singletonList(suggestion);
        };
    }

    /** How many of {@code quotes} {@code tag} is suggested for (#56); {@code model} is fitted on them. */
    static int countSuggestedFor(List<Quote> quotes, HashtagSuggester model, String tag) {
        int count = 0;
        for (Quote quote : quotes) {
            if (model.suggestionFor(quote, tag) != null) count++;
        }
        return count;
    }

    /** {@link #results(List, QuoteQuery, HashtagSuggester, Set)} with the suggestions given per quote. */
    static List<Entry> results(List<Quote> quotes, QuoteQuery query,
                               Function<Quote, List<HashtagSuggester.Suggestion>> suggestions, Set<Integer> skipped) {
        List<Quote> candidates = query.filter(quotes);
        candidates.removeIf(quote -> skipped.contains(quote.getId()));
        List<Entry> ranked = rank(candidates, suggestions);
        if (query.isSuggested() || query.getSuggestedTag() != null) {
            ranked.removeIf(entry -> entry.suggestions.isEmpty());
        }
        return ranked;
    }

    /** The tagging run's next quote: the first of {@link #results}; null when there is none. */
    static Quote next(List<Quote> quotes, QuoteQuery query, HashtagSuggester model, Set<Integer> skipped) {
        List<Entry> results = results(quotes, query, model, skipped);
        return results.isEmpty() ? null : results.get(0).quote;
    }

    /** How many quotes the run still has: the size of {@link #results}. */
    static int remaining(List<Quote> quotes, QuoteQuery query, HashtagSuggester model, Set<Integer> skipped) {
        return results(quotes, query, model, skipped).size();
    }

    /** Each of {@code quotes}' suggestions by quote id; {@code model} is fitted on {@code quotes}. */
    static Map<Integer, List<HashtagSuggester.Suggestion>> suggestionsById(List<Quote> quotes, HashtagSuggester model) {
        Map<Integer, List<HashtagSuggester.Suggestion>> byId = new HashMap<>();
        for (Quote quote : quotes) byId.put(quote.getId(), model.suggest(quote));
        return byId;
    }

    /** How many quotes have at least one suggestion (the "Has suggestions" count). */
    static int countWithSuggestions(Map<Integer, List<HashtagSuggester.Suggestion>> suggestionsById) {
        int count = 0;
        for (List<HashtagSuggester.Suggestion> suggestions : suggestionsById.values()) {
            if (!suggestions.isEmpty()) count++;
        }
        return count;
    }
}
