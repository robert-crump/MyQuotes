package com.example.myquotes;

import android.os.Bundle;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A tagging run (#53, #55, #56): the editor opened from a ranked list, going from one quote to the
 * next. Holds the list's query, whether the user has said to continue (asked once per run), and the
 * quotes left out for the rest of the run (memory only; saved with the editor's state). The order
 * is {@link SuggestionRanking}'s.
 * <p>
 * The run decides what follows each save, so the editor only carries it out: {@link #afterSave}
 * says whether to go back to the list or look for the next quote, and {@link #afterSaveNext} /
 * {@link #afterMoveOn} what to do with the next quote found. What a save means depends on the run's
 * {@link Kind}: from "No hashtag" a save is done with the quote when it tags a quote loaded
 * untagged; from "Has suggestions" every save is; from one tag's "Suggested for" list a save that
 * adds or rejects the tag is. A deciding save leaves the quote out for the rest of the run; one that
 * decides nothing returns to the list before Continue and counts as Skip after it.
 */
final class TaggingRun {
    private static final String KEY_QUERY = "run_query";
    private static final String KEY_CONTINUED = "run_continued";
    private static final String KEY_SKIPPED = "run_skipped";

    /** The toast when a save ends the run, or goes on to the next quote. */
    static final String UPDATED = "Quote updated";

    /** How a save relates to the run. */
    enum Verdict {
        /** Done with the quote for this run. */
        DECIDED,
        /** Still open: back to the list before Continue, a Skip after it. */
        UNDECIDED,
        /** Not part of the run's work (a "No hashtag" run's quote that had hashtags when loaded): back to the list. */
        OUTSIDE
    }

    /** One kind of run per ranked list it starts from. */
    abstract static class Kind {
        /** How a save of a quote that was {@code loadedUntagged} with {@code tags} and {@code rejected} relates to the run. */
        abstract Verdict judge(boolean loadedUntagged, List<String> tags, List<String> rejected);

        abstract String continuePrompt();

        abstract String noMoreMessage();

        String skipTitle() {
            return "Skip to next quote";
        }

        /** The tag pinned first in the Suggested row; null for none. */
        @Nullable
        String pinnedTag() {
            return null;
        }

        static Kind of(QuoteQuery query) {
            String tag = query.getSuggestedTag();
            if (tag != null) return new SuggestedFor(tag);
            return query.isSuggested() ? HAS_SUGGESTIONS : NO_HASHTAG;
        }
    }

    /** From "No hashtag" (#53): done once a quote loaded untagged has hashtags. */
    static final Kind NO_HASHTAG = new Kind() {
        @Override
        Verdict judge(boolean loadedUntagged, List<String> tags, List<String> rejected) {
            if (!loadedUntagged) return Verdict.OUTSIDE;
            return tags.isEmpty() ? Verdict.UNDECIDED : Verdict.DECIDED;
        }

        @Override
        String continuePrompt() {
            return "Continue with next untagged quote?";
        }

        @Override
        String noMoreMessage() {
            return "No more untagged quotes";
        }

        @Override
        String skipTitle() {
            return "Skip to next untagged quote";
        }
    };

    /** From "Has suggestions" (#55): every save is done with the quote, whatever it changed. */
    static final Kind HAS_SUGGESTIONS = new Kind() {
        @Override
        Verdict judge(boolean loadedUntagged, List<String> tags, List<String> rejected) {
            return Verdict.DECIDED;
        }

        @Override
        String continuePrompt() {
            return "Continue with the next quote?";
        }

        @Override
        String noMoreMessage() {
            return "No more suggestions";
        }
    };

    /** From one tag's "Suggested for" list (#56): done once the tag is added or rejected. */
    static final class SuggestedFor extends Kind {
        private final String tag;

        SuggestedFor(String tag) {
            this.tag = tag;
        }

        @Override
        Verdict judge(boolean loadedUntagged, List<String> tags, List<String> rejected) {
            boolean decided = Hashtag.containsIgnoreCase(tags, tag) || Hashtag.containsIgnoreCase(rejected, tag);
            return decided ? Verdict.DECIDED : Verdict.UNDECIDED;
        }

        @Override
        String continuePrompt() {
            return "Continue with the next quote for " + Hashtag.display(tag) + "?";
        }

        @Override
        String noMoreMessage() {
            return "No more suggestions for " + Hashtag.display(tag);
        }

        @Override
        String pinnedTag() {
            return tag;
        }
    }

    /** What {@link #afterSave} decided: back to the list with {@link #UPDATED}, or find the next quote. */
    enum AfterSave { BACK_TO_LIST, FIND_NEXT }

    /** What to do once the next quote is known. */
    static final class Step {
        enum Action {
            /** Back to the list with {@link #message}. */
            FINISH,
            /** Load the next quote in place; toast {@link #message} first when not null. */
            SHOW,
            /** Ask {@link #message} (Continue / Back to list) before loading the next quote. */
            ASK_CONTINUE
        }

        final Action action;
        @Nullable
        final String message;

        private Step(Action action, @Nullable String message) {
            this.action = action;
            this.message = message;
        }

        @Override
        public String toString() {
            return action + (message == null ? "" : "(" + message + ")");
        }
    }

    private final QuoteQuery query;
    private final Kind kind;
    private boolean continued;
    private final Set<Integer> skipped = new HashSet<>();
    // Whether the quote being edited had no hashtags when loaded (what "No hashtag" judges by).
    private boolean loadedUntagged;

    TaggingRun(QuoteQuery query) {
        this.query = query;
        this.kind = Kind.of(query);
    }

    QuoteQuery getQuery() {
        return query;
    }

    /** The tag the editor pins first in the Suggested row; null for none. */
    @Nullable
    String pinnedTag() {
        return kind.pinnedTag();
    }

    /** Whether the user has said to continue, so saves go straight on without asking. */
    boolean isContinued() {
        return continued;
    }

    /** The user chose Continue in the {@link Step.Action#ASK_CONTINUE} dialog. */
    void setContinued() {
        continued = true;
    }

    /** The editor loaded {@code quote}; a later {@link #afterSave} is judged against it. */
    void onQuoteLoaded(Quote quote) {
        loadedUntagged = quote.getTags().isEmpty();
    }

    /** Leaves {@code quoteId} out for the rest of the run (Skip). */
    void skip(int quoteId) {
        skipped.add(quoteId);
    }

    /** Whether {@code quoteId} is left out for the rest of the run. */
    boolean isSkipped(int quoteId) {
        return skipped.contains(quoteId);
    }

    /**
     * After {@code quoteId} was saved with {@code tags} and {@code rejected}: a deciding save, or
     * one that decides nothing once continued, leaves the quote out for the rest of the run and
     * looks for the next quote; else back to the list.
     */
    AfterSave afterSave(int quoteId, List<String> tags, List<String> rejected) {
        Verdict verdict = kind.judge(loadedUntagged, tags, rejected);
        if (verdict == Verdict.OUTSIDE) return AfterSave.BACK_TO_LIST;
        if (verdict == Verdict.UNDECIDED && !continued) return AfterSave.BACK_TO_LIST;
        skip(quoteId);
        return AfterSave.FIND_NEXT;
    }

    /** After {@link AfterSave#FIND_NEXT}: with {@code next} (null for none), ask once, then go straight on. */
    Step afterSaveNext(@Nullable Quote next) {
        if (next == null) return new Step(Step.Action.FINISH, continued ? kind.noMoreMessage() : UPDATED);
        if (continued) return new Step(Step.Action.SHOW, UPDATED);
        return new Step(Step.Action.ASK_CONTINUE, kind.continuePrompt());
    }

    /**
     * Skip or Delete: counts as Continue, so the editor then finds the next quote and carries out
     * {@link #afterMoveOn}.
     */
    void moveOn() {
        continued = true;
    }

    /** After {@link #moveOn}: show {@code next} without asking, or end the run when none is left. */
    Step afterMoveOn(@Nullable Quote next) {
        return next == null ? new Step(Step.Action.FINISH, kind.noMoreMessage()) : new Step(Step.Action.SHOW, null);
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

    /** The Skip action's title. */
    String skipTitle() {
        return kind.skipTitle();
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
