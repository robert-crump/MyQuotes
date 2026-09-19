package com.example.myquotes;

import java.util.List;

/** Persistence seam for the Quote Collection. */
public interface QuoteStore {
    /** The stored quotes; an empty list when nothing is stored. */
    List<Quote> load();

    /** Replaces the stored quotes. An empty list is a real state and is persisted. */
    void save(List<Quote> quotes);

    /** Whether quotes have ever been stored (even an empty list). */
    boolean hasStoredQuotes();
}
