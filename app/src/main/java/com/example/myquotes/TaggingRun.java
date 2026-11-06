package com.example.myquotes;

import android.os.Bundle;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A tagging run (#53, #55): the editor opened from the ranked "No hashtag" or "Has suggestions"
 * list, going from one quote to the next. Holds the list's query, whether the user has said to
 * continue (asked once per run), and the quotes left out for the rest of the run: skipped, and in
 * a "Has suggestions" run also saved (memory only; saved with the editor's state). The order is
 * {@link SuggestionRanking}'s. A run through one tag's "Suggested for" list (#56) is done with a
 * quote once the tag is decided on: added or rejected.
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

    /**
     * A run through the "Has suggestions" list: every save is done with the quote. Else a run
     * through the "No hashtag" list, where a quote is done once it has hashtags.
     */
    boolean isSuggestionsRun() {
        return query.isSuggested();
    }

    /** The tag of a run through its "Suggested for" list (#56); else null. */
    @Nullable
    String getSuggestedTag() {
        return query.getSuggestedTag();
    }

    /** In a "Suggested for" run: whether a quote saved with {@code tags} and {@code rejected} is decided on the tag. */
    boolean isDecided(List<String> tags, List<String> rejected) {
        String tag = getSuggestedTag();
        return tag != null && (Hashtag.containsIgnoreCase(tags, tag) || Hashtag.containsIgnoreCase(rejected, tag));
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
        return SuggestionRanking.next(quotes, query, model, skipped);
    }

    /** How many quotes the run still has, the one being edited included while it is still in the list. */
    int remaining(List<Quote> quotes, HashtagSuggester model) {
        return SuggestionRanking.remaining(quotes, query, model, skipped);
    }

    /** "Continue with next untagged quote?" or "Continue with the next quote?". */
    String continuePrompt() {
        String tag = getSuggestedTag();
        if (tag != null) return "Continue with the next quote for " + Hashtag.display(tag) + "?";
        return isSuggestionsRun() ? "Continue with the next quote?" : "Continue with next untagged quote?";
    }

    /** The toast when the run has nothing left. */
    String noMoreMessage() {
        String tag = getSuggestedTag();
        if (tag != null) return "No more suggestions for " + Hashtag.display(tag);
        return isSuggestionsRun() ? "No more suggestions" : "No more untagged quotes";
    }

    /** The Skip action's title. */
    String skipTitle() {
        return isSuggestionsRun() || getSuggestedTag() != null ? "Skip to next quote" : "Skip to next untagged quote";
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
