package com.example.myquotes;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReadingSession extends ViewModel {
    private static final String TAG = "ReadingSession";

    private final QuoteCollection collection;
    private final MutableLiveData<List<Quote>> deck = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Quote> currentQuote = new MutableLiveData<>();
    private int currentPosition = 0;

    private final Observer<List<Quote>> collectionObserver = this::onCollectionChanged;

    public ReadingSession() {
        collection = MyApplication.getInstance().getQuoteCollection();
        collection.getQuoteList().observeForever(collectionObserver);
    }

    private void onCollectionChanged(List<Quote> newList) {
        if (newList == null) newList = new ArrayList<>();

        List<Quote> currentDeck = deck.getValue();

        if (currentDeck == null || currentDeck.isEmpty()) {
            if (!newList.isEmpty()) {
                List<Quote> shuffled = new ArrayList<>(newList);
                Collections.shuffle(shuffled);
                deck.setValue(shuffled);
                currentPosition = 0;
                currentQuote.setValue(shuffled.get(0));
                Log.d(TAG, "Initialized deck with " + shuffled.size() + " quotes");
            }
            return;
        }

        // Diff the existing deck against the new collection by ID.
        Set<Integer> newIds = new HashSet<>();
        for (Quote q : newList) newIds.add(q.getId());

        List<Quote> updated = new ArrayList<>(currentDeck);
        int removedBeforeCurrent = 0;
        boolean currentWasRemoved = false;
        int i = 0;
        while (i < updated.size()) {
            int id = updated.get(i).getId();
            if (!newIds.contains(id)) {
                if (i < currentPosition) removedBeforeCurrent++;
                if (i == currentPosition) currentWasRemoved = true;
                updated.remove(i);
            } else {
                i++;
            }
        }

        // Replace updated quotes in place (favorite status, text edits, etc.)
        for (int j = 0; j < updated.size(); j++) {
            final int id = updated.get(j).getId();
            for (Quote newQ : newList) {
                if (newQ.getId() == id) {
                    updated.set(j, newQ);
                    break;
                }
            }
        }

        // Append quotes that are new to the collection (appended to end of deck).
        Set<Integer> deckIds = new HashSet<>();
        for (Quote q : updated) deckIds.add(q.getId());
        for (Quote newQ : newList) {
            if (!deckIds.contains(newQ.getId())) {
                updated.add(newQ);
            }
        }

        currentPosition -= removedBeforeCurrent;
        if (currentPosition < 0) currentPosition = 0;
        if (!updated.isEmpty() && currentPosition >= updated.size()) {
            currentPosition = updated.size() - 1;
        }

        deck.setValue(updated);

        if (currentWasRemoved) {
            currentQuote.setValue(updated.isEmpty() ? null : updated.get(currentPosition));
        }
    }

    // ========== PUBLIC INTERFACE ==========

    public LiveData<List<Quote>> getDeck() {
        return deck;
    }

    public LiveData<Quote> getCurrentQuote() {
        return currentQuote;
    }

    // Updates position and records the view in the collection.
    // No-op if the position is unchanged and a current quote is already set,
    // preventing a double view-record when the deck observer repositions the ViewPager.
    public void setPosition(int position) {
        List<Quote> currentDeck = deck.getValue();
        if (currentDeck == null || position < 0 || position >= currentDeck.size()) return;
        if (position == currentPosition && currentQuote.getValue() != null) return;
        currentPosition = position;
        Quote quote = currentDeck.get(position);
        currentQuote.setValue(quote);
        collection.recordView(quote.getId());
    }

    // Finds the quote with the given ID in the deck, sets position, and returns true.
    // Returns false if the quote is not in the current deck (e.g., was deleted).
    public boolean navigateTo(int quoteId) {
        List<Quote> currentDeck = deck.getValue();
        if (currentDeck == null) return false;
        for (int i = 0; i < currentDeck.size(); i++) {
            if (currentDeck.get(i).getId() == quoteId) {
                currentPosition = i;
                currentQuote.setValue(currentDeck.get(i));
                collection.recordView(quoteId);
                return true;
            }
        }
        return false;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    @Override
    protected void onCleared() {
        collection.getQuoteList().removeObserver(collectionObserver);
    }
}
