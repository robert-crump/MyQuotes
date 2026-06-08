package com.example.myquotes;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class QuoteCollection {
    private static final String TAG = "QuoteCollection";

    private final MutableLiveData<List<Quote>> liveQuoteList = new MutableLiveData<>(new ArrayList<>());
    private final Context applicationContext;

    public QuoteCollection(Context context) {
        this.applicationContext = context.getApplicationContext();
    }

    // ========== OBSERVATION ==========

    public LiveData<List<Quote>> getQuoteList() {
        return liveQuoteList;
    }

    // ========== CRUD ==========

    public void add(Quote quote) {
        if (quote == null) {
            Log.w(TAG, "Attempted to add null quote");
            return;
        }
        List<Quote> current = getCurrentList();
        int maxId = current.stream().mapToInt(Quote::getId).max().orElse(0);
        quote.setId(maxId + 1);
        List<Quote> updated = new ArrayList<>(current);
        updated.add(quote);
        liveQuoteList.setValue(updated);
        saveToPreferences(updated);
        Log.d(TAG, "Added quote with ID: " + quote.getId());
    }

    public void update(Quote updatedQuote) {
        if (updatedQuote == null || updatedQuote.getId() == null) {
            Log.w(TAG, "Attempted to update invalid quote");
            return;
        }
        List<Quote> quotes = liveQuoteList.getValue();
        if (quotes == null) return;
        List<Quote> updated = new ArrayList<>(quotes);
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).getId().equals(updatedQuote.getId())) {
                updated.set(i, updatedQuote);
                liveQuoteList.setValue(updated);
                saveToPreferences(updated);
                Log.d(TAG, "Updated quote with ID: " + updatedQuote.getId());
                return;
            }
        }
        Log.w(TAG, "Quote with ID " + updatedQuote.getId() + " not found");
    }

    public void deleteById(int id) {
        List<Quote> quotes = getCurrentList();
        List<Quote> updated = new ArrayList<>();
        boolean removed = false;
        for (Quote quote : quotes) {
            if (quote.getId() != id) {
                updated.add(quote);
            } else {
                removed = true;
            }
        }
        if (removed) {
            liveQuoteList.setValue(updated);
            saveToPreferences(updated);
            Log.d(TAG, "Deleted quote with ID: " + id);
        } else {
            Log.w(TAG, "Quote with ID " + id + " not found");
        }
    }

    public void setList(List<Quote> quotes) {
        if (quotes == null) quotes = new ArrayList<>();
        liveQuoteList.setValue(quotes);
        saveToPreferences(quotes);
        Log.d(TAG, "Set quote list: " + quotes.size() + " quotes");
    }

    // ========== VIEW RECORDING ==========

    // Increments view count and persists without firing LiveData observers —
    // avoids a full deck diff on every swipe.
    public void recordView(int quoteId) {
        List<Quote> quotes = liveQuoteList.getValue();
        if (quotes == null) return;
        for (Quote q : quotes) {
            if (q.getId() == quoteId) {
                q.recordView();
                saveToPreferences(quotes);
                return;
            }
        }
    }

    // ========== QUERIES ==========

    public Quote findById(int id) {
        return getCurrentList().stream()
                .filter(q -> q.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public List<Quote> getFavorites() {
        List<Quote> favorites = getCurrentList().stream()
                .filter(Quote::isFavorite)
                .collect(Collectors.toList());
        favorites.sort((q1, q2) -> Long.compare(q2.getFavoritedAt(), q1.getFavoritedAt()));
        return favorites;
    }

    public void toggleFavorite(int quoteId) {
        Quote quote = findById(quoteId);
        if (quote == null) {
            Log.w(TAG, "Cannot toggle favorite - quote not found: " + quoteId);
            return;
        }
        quote.toggleFavorite();
        update(quote);
    }

    public void trimFields() {
        List<Quote> quotes = liveQuoteList.getValue();
        if (quotes == null || quotes.isEmpty()) return;
        boolean changed = false;
        for (Quote quote : quotes) {
            String author = quote.getAuthor();
            String source = quote.getSource();
            String category = quote.getCategory();
            if (author != null && !author.equals(author.trim())) {
                quote.setAuthor(author.trim());
                changed = true;
            }
            if (source != null && !source.equals(source.trim())) {
                quote.setSource(source.trim());
                changed = true;
            }
            if (category != null && !category.equals(category.trim())) {
                quote.setCategory(category.trim());
                changed = true;
            }
        }
        if (changed) {
            liveQuoteList.setValue(quotes);
            saveToPreferences(quotes);
            Log.d(TAG, "Trimmed trailing spaces from quote fields");
        }
    }

    // ========== HELPERS ==========

    List<Quote> getCurrentList() {
        List<Quote> quotes = liveQuoteList.getValue();
        return quotes != null ? new ArrayList<>(quotes) : new ArrayList<>();
    }

    private void saveToPreferences(List<Quote> quotes) {
        if (quotes != null && !quotes.isEmpty()) {
            new QuotePreferences(applicationContext).saveQuotes(quotes);
        }
    }
}
