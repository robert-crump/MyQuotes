package com.example.myquotes;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The Suggestion Model (#65): the Hashtag suggestions model fitted on the current Quote Collection
 * and the current {@link QuoteEmbeddings} vectors, owned by {@link MyApplication} like
 * {@link Hashtags}. Every screen that needs suggestions observes {@link #getFitted()} instead of
 * fitting its own, so a collection is fitted once, on one fitting thread.
 *
 * <p>It observes the collection and the embeddings' changes. A fit requested while one runs waits
 * for it and then fits the state as it is by then, so quick emissions coalesce into one more fit.
 * A result for an older collection list never publishes (a vectors-only change shows the previous
 * fit until the new one is ready). Per-tag work ({@link #tagPass}), the Tagging run's
 * {@link #onCurrent} and the editor's leave-one-out {@link #fitWithout} run on the same thread.
 * Main-thread state is touched only on the main thread.
 */
final class SuggestionModel {

    /** A fit: immutable, so the fitting thread and the screens can share it. */
    static final class Fitted {
        /** The collection list it was fitted on (compare by identity). */
        final List<Quote> quotes;
        /** {@link QuoteEmbeddings#getChanges()}' value it was fitted with. */
        final Integer vectorsVersion;
        final Map<Integer, float[]> vectors;
        final HashtagSuggester model;
        /** Each quote's leave-one-out suggestions ({@link HashtagSuggester#suggest(Quote)}). */
        final Map<Integer, List<HashtagSuggester.Suggestion>> suggestionsById;
        /** The quotes with suggestions: the "Has suggestions" count. */
        final int countWithSuggestions;
        private final Map<String, TagPass> tagPasses = new ConcurrentHashMap<>();

        Fitted(List<Quote> quotes, Integer vectorsVersion, Map<Integer, float[]> vectors) {
            this.quotes = quotes;
            this.vectorsVersion = vectorsVersion;
            this.vectors = vectors;
            this.model = HashtagSuggester.fit(quotes, vectors);
            this.suggestionsById = Collections.unmodifiableMap(SuggestionRanking.suggestionsById(quotes, model));
            this.countWithSuggestions = SuggestionRanking.countWithSuggestions(suggestionsById);
        }

        /** {@code tag}'s pass when it was already computed; else null. */
        @Nullable
        TagPass cachedTagPass(String tag) {
            return tagPasses.get(Hashtag.key(tag));
        }

        /** Computes (or reuses) {@code tag}'s pass; call off the main thread. */
        TagPass tagPass(String tag) {
            return tagPasses.computeIfAbsent(Hashtag.key(tag), key -> new TagPass(this, tag));
        }
    }

    /**
     * One tag's view of a fit: the "Suggested for" list (each quote's {@link HashtagSuggester#suggestionFor}
     * with its explanation line, #56), its size for the hashtag sheet, and the tag's profile (#52).
     */
    static final class TagPass {
        final String tag;
        final Map<Integer, HashtagSuggester.Suggestion> suggestions;
        final Map<Integer, String> lines;
        final HashtagSuggester.TagProfile profile;

        TagPass(Fitted fitted, String tag) {
            this.tag = tag;
            Map<Integer, HashtagSuggester.Suggestion> byId = new HashMap<>();
            Map<Integer, String> linesById = new HashMap<>();
            for (Quote quote : fitted.quotes) {
                HashtagSuggester.Suggestion suggestion = fitted.model.suggestionFor(quote, tag);
                if (suggestion == null) continue;
                byId.put(quote.getId(), suggestion);
                linesById.put(quote.getId(),
                        HashtagSuggestionText.suggestedForLine(fitted.model.explain(quote, tag)));
            }
            suggestions = Collections.unmodifiableMap(byId);
            lines = Collections.unmodifiableMap(linesById);
            profile = fitted.model.profile(tag);
        }

        /** How many quotes the tag is suggested for. */
        int count() {
            return suggestions.size();
        }
    }

    /** A pending answer; {@link #cancel()} drops it (e.g. when its screen goes away). */
    static final class Request {
        private volatile boolean cancelled;

        void cancel() {
            cancelled = true;
        }

        boolean isCancelled() {
            return cancelled;
        }
    }

    private final LiveData<List<Quote>> quotes;
    private final LiveData<Integer> vectorChanges;
    private final Function<List<Quote>, Map<Integer, float[]>> vectorsFor;
    private final Executor fitter;
    private final Executor main;
    private final MutableLiveData<Fitted> fitted = new MutableLiveData<>();
    // Main thread only.
    private boolean running;
    private final List<Runnable> waiting = new ArrayList<>();

    /** Production: over the collection and the embeddings; {@code fitter} is a single thread. */
    SuggestionModel(QuoteCollection collection, QuoteEmbeddings embeddings, Executor fitter, Executor main) {
        this(collection.getQuoteList(), embeddings.getChanges(), embeddings::vectorsFor, fitter, main);
    }

    /**
     * {@code vectorsFor} gives the vectors of a list (called on the main thread); {@code vectorChanges}
     * emits when they change. {@code main} runs on the main thread.
     */
    SuggestionModel(LiveData<List<Quote>> quotes, LiveData<Integer> vectorChanges,
                    Function<List<Quote>, Map<Integer, float[]>> vectorsFor, Executor fitter, Executor main) {
        this.quotes = quotes;
        this.vectorChanges = vectorChanges;
        this.vectorsFor = vectorsFor;
        this.fitter = fitter;
        this.main = main;
        quotes.observeForever(list -> request());
        vectorChanges.observeForever(changes -> request());
    }

    /** The latest fit; emits on the main thread when a newer one is ready. */
    LiveData<Fitted> getFitted() {
        return fitted;
    }

    private boolean isCurrent(@Nullable Fitted fit) {
        return fit != null && fit.quotes == quotes.getValue();
    }

    /** Main thread: fits the current state unless that is published or a fit is running. */
    private void request() {
        List<Quote> list = quotes.getValue();
        if (list == null || running) return;
        Integer version = vectorChanges.getValue();
        Fitted published = fitted.getValue();
        if (isCurrent(published) && Objects.equals(published.vectorsVersion, version)) return;
        running = true;
        Map<Integer, float[]> vectors = vectorsFor.apply(list);
        fitter.execute(() -> {
            Fitted fit = new Fitted(list, version, vectors);
            main.execute(() -> completed(fit));
        });
    }

    private void completed(Fitted fit) {
        running = false;
        if (isCurrent(fit)) {
            fitted.setValue(fit);
            Iterator<Runnable> it = waiting.iterator();
            while (it.hasNext()) {
                Runnable waiter = it.next();
                it.remove();
                waiter.run();
            }
        }
        request(); // the state may have moved on meanwhile
    }

    /**
     * Once a fit on the collection's current list is published (at once if it is), runs {@code work}
     * on it on the fitting thread, then {@code onMain} with the result. The Tagging run uses it
     * after a save, for the next quote and the count left.
     */
    <T> Request onCurrent(Function<Fitted, T> work, Consumer<T> onMain) {
        Request request = new Request();
        Runnable start = () -> {
            Fitted fit = fitted.getValue();
            fitter.execute(() -> {
                if (request.isCancelled()) return;
                T result = work.apply(fit);
                main.execute(() -> {
                    if (!request.isCancelled()) onMain.accept(result);
                });
            });
        };
        if (isCurrent(fitted.getValue())) {
            start.run();
        } else {
            waiting.add(start);
            request();
        }
        return request;
    }

    /** {@code tag}'s pass on {@code fit}: at once when computed, else on the fitting thread. */
    Request tagPass(Fitted fit, String tag, Consumer<TagPass> onMain) {
        Request request = new Request();
        TagPass cached = fit.cachedTagPass(tag);
        if (cached != null) {
            onMain.accept(cached);
            return request;
        }
        fitter.execute(() -> {
            if (request.isCancelled()) return;
            TagPass pass = fit.tagPass(tag);
            main.execute(() -> {
                if (!request.isCancelled()) onMain.accept(pass);
            });
        });
        return request;
    }

    /**
     * A model fitted on {@code base}'s quotes without the quote {@code quoteId} (the editor's
     * Suggested row judges the edited quote against the others), on the fitting thread.
     */
    Request fitWithout(Fitted base, int quoteId, Consumer<HashtagSuggester> onMain) {
        Request request = new Request();
        fitter.execute(() -> {
            if (request.isCancelled()) return;
            List<Quote> others = new ArrayList<>(base.quotes.size());
            for (Quote quote : base.quotes) {
                if (quote.getId() == null || quote.getId() != quoteId) others.add(quote);
            }
            HashtagSuggester model = HashtagSuggester.fit(others, base.vectors);
            main.execute(() -> {
                if (!request.isCancelled()) onMain.accept(model);
            });
        });
        return request;
    }
}
