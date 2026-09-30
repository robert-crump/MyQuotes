package com.example.myquotes;

import java.util.ArrayList;
import java.util.List;

class InMemoryQuoteStore implements QuoteStore {
    List<Quote> saved;
    int saveCount = 0;
    boolean oldFormat = false;

    InMemoryQuoteStore() {
    }

    InMemoryQuoteStore(List<Quote> initial) {
        this.saved = new ArrayList<>(initial);
    }

    @Override
    public List<Quote> load() {
        return saved == null ? new ArrayList<>() : new ArrayList<>(saved);
    }

    @Override
    public void save(List<Quote> quotes) {
        saved = new ArrayList<>(quotes);
        saveCount++;
        oldFormat = false;
    }

    @Override
    public boolean hasStoredQuotes() {
        return saved != null;
    }

    @Override
    public boolean isOldFormat() {
        return oldFormat;
    }
}
