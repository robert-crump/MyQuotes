package com.example.myquotes;

import android.os.Bundle;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A tagging run (#53): the editor opened from the "No hashtag" list, going from one untagged
 * quote to the next. Holds the list's query, whether the user has said to continue (asked once per
 * run), and the quotes skipped in this run (memory only; saved with the editor's state). The order
 * is {@link UntaggedRanking}'s.
 */
final class TaggingRun {
    private static final String KEY_QUERY = "run_query";
    private static final String KEY_CONTINUED = "run_continued";
    private static final String KEY_SKIPPED = "run_skipped";

    private final QuoteQuery query;
    private boolean continued;
    private final Set<Integer> skipped = new HashSet<>();

    TaggingRun(QuoteQuery query) {
        this.query = query;
    }

    QuoteQuery getQuery() {
        return query;
    }

    /** Whether the user has said to continue, so saves go straight on without asking. */
    boolean isContinued() {
        return continued;
    }

    void setContinued() {
        continued = true;
    }

    /** Leaves {@code quoteId} out for the rest of the run. */
    void skip(int quoteId) {
        skipped.add(quoteId);
    }

    /** The next quote, best ranked first; null when the run has none left. */
    @Nullable
    Quote next(List<Quote> quotes, HashtagSuggester model) {
        return UntaggedRanking.next(quotes, query, model, skipped);
    }

    /** How many quotes the run still has, the one being edited included while it is untagged. */
    int remaining(List<Quote> quotes) {
        return UntaggedRanking.remaining(quotes, query, skipped);
    }

    Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putBundle(KEY_QUERY, query.toBundle());
        bundle.putBoolean(KEY_CONTINUED, continued);
        bundle.putIntegerArrayList(KEY_SKIPPED, new ArrayList<>(skipped));
        return bundle;
    }

    /** Reads back {@link #toBundle}; null when the bundle holds no run. */
    @Nullable
    static TaggingRun fromBundle(@Nullable Bundle bundle) {
        if (bundle == null) return null;
        QuoteQuery query = QuoteQuery.fromBundle(bundle.getBundle(KEY_QUERY));
        if (query == null) return null;
        TaggingRun run = new TaggingRun(query);
        run.continued = bundle.getBoolean(KEY_CONTINUED);
        List<Integer> skipped = bundle.getIntegerArrayList(KEY_SKIPPED);
        if (skipped != null) run.skipped.addAll(skipped);
        return run;
    }
}
