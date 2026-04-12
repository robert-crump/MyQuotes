package com.example.myquotes;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class QuoteViewModel extends ViewModel {
    private static final String TAG = "QuoteViewModel";

    private final MutableLiveData<List<Quote>> liveQuoteList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Quote> currentlyDisplayedQuote = new MutableLiveData<>();
    private final Random random = new Random();
    private List<Quote> shuffledQuoteList = null;
    private int currentQuoteIndex = 0;
    private Context applicationContext;

    public void setApplicationContext(Context context) {
        this.applicationContext = context.getApplicationContext();
    }

    private void saveQuotesToPreferences() {
        if (applicationContext != null) {
            List<Quote> quotes = getCurrentList();
            if (!quotes.isEmpty()) {
                QuotePreferences prefs = new QuotePreferences(applicationContext);
                prefs.saveQuotes(quotes);
            }
        }
    }

    private void saveQuotesToPreferences(List<Quote> quotes) {
        if (applicationContext != null && quotes != null && !quotes.isEmpty()) {
            QuotePreferences prefs = new QuotePreferences(applicationContext);
            prefs.saveQuotes(quotes);
        }
    }

    // ========== GETTER ==========

    public LiveData<List<Quote>> getQuoteList() {
        return liveQuoteList;
    }


    // ========== QUOTE LIST OPERATIONS ==========

    public void updateQuoteList(List<Quote> quotes) {
        if (quotes == null) {
            Log.w(TAG, "Attempted to update with null list");
            return;
        }
        List<Quote> defensiveCopy = new ArrayList<>(quotes);
        liveQuoteList.postValue(defensiveCopy);
        saveQuotesToPreferences();
        Log.d(TAG, "Updated quote list: " + quotes.size() + " quotes");
    }

    public void setQuoteList(List<Quote> quotes) {
        if (quotes == null) {
            quotes = new ArrayList<>();
        }

        final List<Quote> finalQuotes = quotes;

        liveQuoteList.setValue(finalQuotes);
        saveQuotesToPreferences(finalQuotes);

        if (shuffledQuoteList != null) {
            shuffledQuoteList.removeIf(q -> !finalQuotes.contains(q));
        }

        Log.d(TAG, "Set quote list: " + finalQuotes.size() + " quotes");
    }

    // Creates the shuffled list once per session
    public void initializeShuffledList() {
        List<Quote> quotes = getCurrentList();
        if (!quotes.isEmpty() && shuffledQuoteList == null) {
            shuffledQuoteList = new ArrayList<>(quotes);
            Collections.shuffle(shuffledQuoteList);
            currentQuoteIndex = 0;
            Log.d(TAG, "Initialized shuffled list with " + shuffledQuoteList.size() + " quotes");
        }
    }

    public Quote getCurrentQuoteFromShuffledList() {
        if (shuffledQuoteList == null || shuffledQuoteList.isEmpty()) {
            return null;
        }
        return shuffledQuoteList.get(currentQuoteIndex);
    }

    public List<Quote> getShuffledQuoteList() {
        if (shuffledQuoteList == null || shuffledQuoteList.isEmpty()) {
            initializeShuffledList();
        }
        if (shuffledQuoteList == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(shuffledQuoteList);
    }

    public void setCurrentQuoteIndex(int index) {
        if (shuffledQuoteList != null && index >= 0 && index < shuffledQuoteList.size()) {
            currentQuoteIndex = index;
            Quote quote = shuffledQuoteList.get(currentQuoteIndex);
            setCurrentlyDisplayedQuote(quote);
        }
    }

    public void addQuote(Quote quote) {
        if (quote == null) {
            Log.w(TAG, "Attempted to add null quote");
            return;
        }

        List<Quote> currentQuotes = getCurrentList();
        List<Quote> updatedQuotes = new ArrayList<>(currentQuotes);

        // Auto-generate an ID one above the current maximum
        int maxId = updatedQuotes.stream()
                .mapToInt(Quote::getId)
                .max()
                .orElse(0);
        quote.setId(maxId + 1);

        updatedQuotes.add(quote);
        liveQuoteList.setValue(updatedQuotes);
        saveQuotesToPreferences(updatedQuotes); 
        // Also add to shuffled list so counter updates immediately
        if (shuffledQuoteList != null) {
            shuffledQuoteList.add(quote);
        }

        Log.d(TAG, "Added quote with ID: " + quote.getId());
    }

    public void updateQuote(Quote updatedQuote) {
        if (updatedQuote == null || updatedQuote.getId() == null) {
            Log.w(TAG, "Attempted to update invalid quote");
            return;
        }

        List<Quote> quotes = liveQuoteList.getValue();
        if (quotes == null) {
            Log.w(TAG, "Cannot update quote - quote list is null");
            return;
        }

        boolean quoteExists = false;
        for (Quote q : quotes) {
            if (q.getId().equals(updatedQuote.getId())) {
                quoteExists = true;
                break;
            }
        }

        if (!quoteExists) {
            Log.w(TAG, "Cannot update quote #" + updatedQuote.getId() + " - does not exist (may have been deleted)");
            return;
        }

        List<Quote> updatedQuotes = new ArrayList<>(quotes);

        for (int i = 0; i < updatedQuotes.size(); i++) {
            if (updatedQuotes.get(i).getId().equals(updatedQuote.getId())) {
                updatedQuotes.set(i, updatedQuote);
                liveQuoteList.setValue(updatedQuotes); // synchronous setValue so callers see the update immediately
                saveQuotesToPreferences(updatedQuotes);                 Log.d(TAG, "Updated quote with ID: " + updatedQuote.getId());

                // Update currently displayed quote if it's the same
                Quote current = currentlyDisplayedQuote.getValue();
                if (current != null && current.getId().equals(updatedQuote.getId())) {
                    currentlyDisplayedQuote.setValue(updatedQuote);
                }

                // Update in shuffledQuoteList
                if (shuffledQuoteList != null) {
                    for (int j = 0; j < shuffledQuoteList.size(); j++) {
                        if (shuffledQuoteList.get(j).getId().equals(updatedQuote.getId())) {
                            shuffledQuoteList.set(j, updatedQuote);
                            break;
                        }
                    }
                }

                return;
            }
        }
        Log.w(TAG, "Quote with ID " + updatedQuote.getId() + " not found");
    }

    public void deleteQuoteById(int quoteId) {
        List<Quote> quotes = getCurrentList();

        List<Quote> updatedQuotes = new ArrayList<>();
        boolean removed = false;

        for (Quote quote : quotes) {
            if (quote.getId() != quoteId) {
                updatedQuotes.add(quote);
            } else {
                removed = true;
            }
        }

        if (removed) {
            liveQuoteList.setValue(updatedQuotes); // synchronous setValue so callers see the update immediately
            saveQuotesToPreferences(updatedQuotes); 
            if (shuffledQuoteList != null) {
                shuffledQuoteList.removeIf(quote -> quote.getId() == quoteId);
            }

            Log.d(TAG, "Deleted quote with ID: " + quoteId);
        } else {
            Log.w(TAG, "Quote with ID " + quoteId + " not found");
        }
    }

    // ========== QUOTE RETRIEVAL ==========

    public Quote getQuoteById(int quoteId) {
        List<Quote> quotes = getCurrentList();
        return quotes.stream()
                .filter(q -> q.getId() == quoteId)
                .findFirst()
                .orElse(null);
    }

    // Weighted-random selection, biased toward favorites and less-shown quotes
    public Quote getNextQuote() {
        List<Quote> quotes = getCurrentList();
        if (quotes.isEmpty()) {
            Log.w(TAG, "Quote list is empty");
            return null;
        }

        Quote currentQuote = currentlyDisplayedQuote.getValue();

        // Prefer quotes not shown in the last 7 days
        long oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
        List<Quote> candidates = quotes.stream()
                .filter(q -> !q.equals(currentQuote))
                .filter(q -> q.getLastShown() < oneWeekAgo || q.getLastShown() == 0)
                .collect(Collectors.toList());

        // Fallback: all quotes except the current one
        if (candidates.isEmpty()) {
            candidates = quotes.stream()
                    .filter(q -> !q.equals(currentQuote))
                    .collect(Collectors.toList());
        }

        if (candidates.isEmpty()) {
            return quotes.get(0);
        }

        return selectWeightedRandom(candidates);
    }

    private Quote selectWeightedRandom(List<Quote> quotes) {
        float totalWeight = 0f;
        for (Quote quote : quotes) {
            totalWeight += quote.calculateScore();
        }

        float randomValue = random.nextFloat() * totalWeight;

        float currentWeight = 0f;
        for (Quote quote : quotes) {
            currentWeight += quote.calculateScore();
            if (currentWeight >= randomValue) {
                return quote;
            }
        }

        return quotes.get(random.nextInt(quotes.size()));
    }

    // ========== RATING & FAVORITES ==========

    public void rateQuote(int quoteId, int delta) {
        Quote quote = getQuoteById(quoteId);
        if (quote == null) {
            Log.w(TAG, "Cannot rate quote - not found: " + quoteId);
            return;
        }

        if (delta > 0) {
            quote.incrementRating();
        } else {
            quote.decrementRating();
        }

        updateQuote(quote);
        Log.d(TAG, "Rated quote " + quoteId + ": new rating = " + quote.getRating());
    }

    public void toggleFavorite(int quoteId) {
        Quote quote = getQuoteById(quoteId);
        if (quote == null) {
            Log.w(TAG, "Cannot toggle favorite - quote not found: " + quoteId);
            return;
        }

        quote.toggleFavorite();
        updateQuote(quote);
        Log.d(TAG, "Toggled favorite for quote " + quoteId + ": " + quote.isFavorite());
    }

    public List<Quote> getFavoriteQuotes() {
        List<Quote> quotes = getCurrentList();
        List<Quote> favorites = quotes.stream()
                .filter(Quote::isFavorite)
                .collect(Collectors.toList());
        // Sortiere nach favoritedAt (neueste zuerst)
        favorites.sort((q1, q2) -> Long.compare(q2.getFavoritedAt(), q1.getFavoritedAt()));
        return favorites;
    }

    public void trimQuoteFields() {
        List<Quote> quotes = liveQuoteList.getValue();
        if (quotes == null || quotes.isEmpty()) return;

        boolean changed = false;
        for (Quote quote : quotes) {
            String author = quote.getAuthor();
            String source = quote.getSource();
            String category = quote.getCategory();

            if (!author.equals(author.trim())) {
                quote.setAuthor(author.trim());
                changed = true;
            }
            if (!source.equals(source.trim())) {
                quote.setSource(source.trim());
                changed = true;
            }
            if (!category.equals(category.trim())) {
                quote.setCategory(category.trim());
                changed = true;
            }
        }

        if (changed) {
            liveQuoteList.setValue(quotes);
            saveQuotesToPreferences(quotes);
            Log.d(TAG, "Trimmed trailing spaces from quote fields");
        }
    }

    // ========== DISPLAY MANAGEMENT ==========

    public void setCurrentlyDisplayedQuote(Quote quote) {
        if (quote != null) {
            quote.recordView();
            // Do NOT call updateQuote() here — that would persist on every swipe.
            // recordView() only updates transient in-memory state.
        }
        currentlyDisplayedQuote.setValue(quote);
    }

    // ========== HELPER METHODS ==========

    List<Quote> getCurrentList() {
        List<Quote> quotes = liveQuoteList.getValue();
        return quotes != null ? new ArrayList<>(quotes) : new ArrayList<>();
    }

    public static class QuoteStatistics {
        public final int totalQuotes;
        public final int favoriteCount;
        public final double averageRating;

        public QuoteStatistics(int total, int favorites, double avgRating) {
            this.totalQuotes = total;
            this.favoriteCount = favorites;
            this.averageRating = avgRating;
        }
    }
}